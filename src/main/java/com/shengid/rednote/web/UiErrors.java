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
        || r.getRequestURI().contains("/preview/files/"))
      return ResponseEntity.status(e.status)
          .body(new Views.Failure("UI_" + e.status, e.getMessage(), e.fields));
    ModelAndView view = new ModelAndView("error/unavailable");
    view.setStatus(org.springframework.http.HttpStatus.valueOf(e.status));
    view.addObject("message", e.getMessage());
    return view;
  }

  @ExceptionHandler({
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalidRequest() {
    return ResponseEntity.badRequest()
        .body(new Views.Failure("INVALID_REQUEST", "请求参数不正确，请检查输入", Map.of()));
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> size() {
    return ResponseEntity.status(413)
        .body(new Views.Failure("FILE_TOO_LARGE", "单张图片不能超过 10 MB", Map.of()));
  }
}
