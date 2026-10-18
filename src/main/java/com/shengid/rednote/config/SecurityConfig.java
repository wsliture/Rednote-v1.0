package com.shengid.rednote.config;

import com.shengid.rednote.security.*;
import com.shengid.rednote.service.IdentityService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  @Bean
  static PasswordEncoder passwordEncoder() {
    var pbkdf = new Pbkdf2PasswordEncoder("", 16, 600000, 256);
    pbkdf.setAlgorithm(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    return new DelegatingPasswordEncoder("pbkdf2-rednote-v1", Map.of("pbkdf2-rednote-v1", pbkdf));
  }

  static String safeReturn(HttpServletRequest req) {
    String path = req.getParameter("returnTo");
    if (path == null || path.length() > 2048) return "/";
    try {
      String decoded = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8);
      if (!decoded.startsWith("/")
          || decoded.startsWith("//")
          || decoded.contains("\\")
          || decoded.chars().anyMatch(c -> c < 32 || c == 127)) return "/";
      var uri = java.net.URI.create(path);
      if (uri.isAbsolute() || uri.getRawAuthority() != null || path.startsWith("/logout"))
        return "/";
    } catch (IllegalArgumentException e) {
      return "/";
    }
    return path;
  }

  static boolean json(HttpServletRequest r) {
    String path = r.getRequestURI().substring(r.getContextPath().length());
    return path.startsWith("/actions/")
        || path.startsWith("/fragments/")
        || path.startsWith("/files/")
        || "1".equals(r.getHeader("X-Rednote-Form"));
  }

  public static void failure(
      HttpServletRequest req, HttpServletResponse res, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json;charset=UTF-8");
    // Fixed messages; only generated request IDs are included.
    res.getWriter()
        .write(
            "{\"code\":\""
                + code
                + "\",\"message\":\""
                + message
                + "\",\"fields\":{},\"requestId\":\""
                + req.getAttribute("requestId")
                + "\"}");
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, DatabaseAuthenticationProvider provider, IdentityService identity)
      throws Exception {
    http.authenticationProvider(provider)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico",
                        "/vendor/**",
                        "/",
                        "/search",
                        "/login",
                        "/register",
                        "/error",
                        "/fragments/feed",
                        "/fragments/comments/**")
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET, "/files/**", "/actuator/health")
                    .permitAll()
                    .requestMatchers("/notes/new", "/notes/*/edit")
                    .authenticated()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET, "/notes/*", "/users/*")
                    .permitAll()
                    .requestMatchers(
                        "/admin/**", "/actions/admin/**", "/fragments/admin/**", "/actuator/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginPage("/login")
                    .successHandler(
                        (req, res, auth) -> {
                          if (!(auth.getPrincipal() instanceof SessionUser p)
                              || !identity.valid(p)) {
                            SecurityContextHolder.clearContext();
                            if (req.getSession(false) != null) req.getSession(false).invalidate();
                            res.sendRedirect(req.getContextPath() + "/login?error");
                            return;
                          }
                          res.setStatus(303);
                          res.setHeader("Location", req.getContextPath() + safeReturn(req));
                        })
                    .failureHandler(
                        (req, res, e) ->
                            res.sendRedirect(
                                req.getContextPath()
                                    + "/login?"
                                    + (e
                                            instanceof
                                            org.springframework.security.authentication
                                                .DisabledException
                                        ? "disabled"
                                        : "error")
                                    + "&returnTo="
                                    + URLEncoder.encode(safeReturn(req), StandardCharsets.UTF_8))))
        .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/"))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          if (json(req)) failure(req, res, 401, "AUTH_REQUIRED", "登录已过期，请先登录");
                          else
                            res.sendRedirect(
                                req.getContextPath()
                                    + "/login?returnTo="
                                    + URLEncoder.encode(
                                        req.getRequestURI().substring(req.getContextPath().length())
                                            + (req.getQueryString() == null
                                                ? ""
                                                : "?" + req.getQueryString()),
                                        StandardCharsets.UTF_8));
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          var auth = SecurityContextHolder.getContext().getAuthentication();
                          boolean anonymous =
                              auth == null
                                  || auth
                                      instanceof
                                      org.springframework.security.authentication
                                          .AnonymousAuthenticationToken;
                          if (json(req))
                            failure(
                                req,
                                res,
                                anonymous ? 401 : 403,
                                anonymous ? "AUTH_REQUIRED" : "FORBIDDEN",
                                anonymous ? "请先登录" : "无权限或安全校验失败，请刷新");
                          else res.sendError(403);
                        }));
    http.addFilterBefore(
        new OncePerRequestFilter() {
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            var a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.getPrincipal() instanceof SessionUser p && !identity.valid(p)) {
              SecurityContextHolder.clearContext();
              if (req.getSession(false) != null) req.getSession(false).invalidate();
            }
            chain.doFilter(req, res);
          }
        },
        org.springframework.security.web.csrf.CsrfFilter.class);
    return http.build();
  }
}
