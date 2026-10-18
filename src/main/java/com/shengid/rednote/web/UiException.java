package com.shengid.rednote.web;

import java.util.Map;

public class UiException extends RuntimeException {
  public final int status;
  public final Map<String, String> fields;

  public UiException(int status, String message) {
    this(status, message, Map.of());
  }

  public UiException(int status, String message, Map<String, String> fields) {
    super(message);
    this.status = status;
    this.fields = fields;
  }
}
