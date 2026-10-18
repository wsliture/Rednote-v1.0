package com.shengid.rednote.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    req.setAttribute("requestId", id);
    res.setHeader("X-Request-ID", id);
    MDC.put("requestId", id);
    long started = System.nanoTime();
    try {
      chain.doFilter(req, res);
    } finally {
      LoggerFactory.getLogger(getClass())
          .info(
              "request method={} path={} status={} durationMs={}",
              req.getMethod(),
              req.getRequestURI(),
              res.getStatus(),
              (System.nanoTime() - started) / 1000000);
      MDC.remove("requestId");
    }
  }
}
