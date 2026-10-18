package com.shengid.rednote.preview;

import com.shengid.rednote.security.SessionUser;
import com.shengid.rednote.web.UiException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@Profile("preview")
public class PreviewSecurity {
  @Bean
  static PasswordEncoder passwordEncoder() {
    return new Pbkdf2PasswordEncoder(
        "", 16, 600000, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
  }

  @Bean
  UserDetailsService users(PreviewStore store) {
    return name -> {
      try {
        var a = store.account(name);
        return new SessionUser(
            a.id,
            a.version,
            a.username,
            a.password,
            List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_" + a.role)),
            a.status.equals("正常"));
      } catch (UiException e) {
        throw new UsernameNotFoundException("账号或密码错误");
      }
    };
  }

  static String returnPath(HttpServletRequest request) {
    String path = request.getParameter("returnTo");
    if (path == null
        || !path.startsWith("/")
        || path.startsWith("//")
        || path.contains("\\")
        || path.chars().anyMatch(c -> c < 32 || c == 127)) return "/";
    return path;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, PreviewStore store) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/vendor/**",
                        "/",
                        "/search",
                        "/login",
                        "/register",
                        "/error",
                        "/preview/files/**",
                        "/fragments/feed",
                        "/fragments/comments/**")
                    .permitAll()
                    .requestMatchers("/notes/new", "/notes/*/edit")
                    .authenticated()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET, "/notes/*", "/users/*")
                    .permitAll()
                    .requestMatchers("/admin/**", "/actions/admin/**", "/fragments/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginPage("/login")
                    .successHandler(
                        (request, response, authentication) -> {
                          String path = returnPath(request);
                          response.setStatus(303);
                          response.setHeader("Location", request.getContextPath() + path);
                        })
                    .failureHandler(
                        (request, response, exception) ->
                            response.sendRedirect(
                                request.getContextPath()
                                    + "/login?"
                                    + (exception
                                            instanceof
                                            org.springframework.security.authentication
                                                .DisabledException
                                        ? "disabled"
                                        : "error")
                                    + "&returnTo="
                                    + java.net.URLEncoder.encode(
                                        returnPath(request),
                                        java.nio.charset.StandardCharsets.UTF_8))))
        .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/"))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          if (req.getRequestURI().contains("/actions/")
                              || req.getRequestURI().contains("/fragments/")
                              || "1".equals(req.getHeader("X-Rednote-Form"))) {
                            res.setStatus(401);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter()
                                .write("{\"code\":\"AUTH_REQUIRED\",\"message\":\"登录已过期，请先登录\"}");
                          } else
                            res.sendRedirect(
                                req.getContextPath()
                                    + "/login?returnTo="
                                    + java.net.URLEncoder.encode(
                                        req.getRequestURI().substring(req.getContextPath().length())
                                            + (req.getQueryString() == null
                                                ? ""
                                                : "?" + req.getQueryString()),
                                        java.nio.charset.StandardCharsets.UTF_8));
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          if (!req.getRequestURI().contains("/actions/")
                              && !req.getRequestURI().contains("/fragments/")
                              && !"1".equals(req.getHeader("X-Rednote-Form"))) {
                            res.sendError(403, "无权限访问");
                            return;
                          }
                          var current = SecurityContextHolder.getContext().getAuthentication();
                          boolean anonymous =
                              current == null
                                  || current
                                      instanceof
                                      org.springframework.security.authentication
                                          .AnonymousAuthenticationToken;
                          res.setStatus(anonymous ? 401 : 403);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter()
                              .write(
                                  anonymous
                                      ? "{\"code\":\"AUTH_REQUIRED\",\"message\":\"登录已过期，请先登录\"}"
                                      : "{\"code\":\"FORBIDDEN\",\"message\":\"无权限或安全校验失败，请刷新\"}");
                        }));
    http.addFilterBefore(
        new OncePerRequestFilter() {
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof SessionUser p) {
              boolean valid;
              try {
                valid =
                    store.version(p.id()) == p.version()
                        && store.user(p.id()).status().equals("正常");
              } catch (Exception e) {
                valid = false;
              }
              if (!valid) {
                SecurityContextHolder.clearContext();
                if (req.getSession(false) != null) req.getSession(false).invalidate();
              }
            }
            chain.doFilter(req, res);
          }
        },
        UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
