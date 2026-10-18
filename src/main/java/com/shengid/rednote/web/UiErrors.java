package com.shengid.rednote.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class UiErrors {
  @ExceptionHandler(UiException.class)
  Object error(UiException e, HttpServletRequest r) {
    if (r.getRequestURI().contains("/actions/")
        || r.getRequestURI().contains("/fragments/")
        || r.getRequestURI().contains("/files/"))
      return ResponseEntity.status(e.status)
          .body(new Views.Failure("UI_" + e.status, e.getMessage(), e.fields));
    ModelAndView view = new ModelAndView("error/unavailable");
    view.setStatus(org.springframework.http.HttpStatus.valueOf(e.status));
    view.addObject("message", e.getMessage());
    return view;
  }

  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
  ResponseEntity<?> invalidFields(org.springframework.web.bind.MethodArgumentNotValidException e) {
    Map<String, String> fields = new java.util.LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return ResponseEntity.badRequest().body(new Views.Failure("INVALID_REQUEST", "请检查输入", fields));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalidRequest() {
    return ResponseEntity.badRequest()
        .body(new Views.Failure("INVALID_REQUEST", "请求参数不正确，请检查输入", Map.of()));
  }

  @ExceptionHandler({
    org.springframework.dao.CannotAcquireLockException.class,
    org.springframework.dao.OptimisticLockingFailureException.class
  })
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(new Views.Failure("CONFLICT", "数据发生冲突，请刷新后重试", Map.of()));
  }

  @ExceptionHandler({
    org.springframework.web.servlet.resource.NoResourceFoundException.class,
    org.springframework.web.servlet.NoHandlerFoundException.class
  })
  Object missing(Exception e, HttpServletRequest r) {
    return error(new UiException(404, "内容不存在"), r);
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  Object method(Exception e, HttpServletRequest r) {
    return error(new UiException(405, "请求方法不支持"), r);
  }

  @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
  Object missingParameter(Exception e, HttpServletRequest r) {
    return error(new UiException(400, "请求参数不完整"), r);
  }

  @ExceptionHandler(Exception.class)
  Object unexpected(Exception e, HttpServletRequest r) {
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error("request_failed id={}", r.getAttribute("requestId"), e);
    return error(new UiException(500, "服务暂时不可用，请稍后重试"), r);
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> size() {
    return ResponseEntity.status(413)
        .body(new Views.Failure("FILE_TOO_LARGE", "单张图片不能超过 10 MB", Map.of()));
  }
}
