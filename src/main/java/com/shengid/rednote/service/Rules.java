package com.shengid.rednote.service;

import com.shengid.rednote.web.UiException;
import java.util.*;

public final class Rules {
  private Rules() {}

  public static String text(String field, String value, int min, int max) {
    String s = value == null ? "" : value.strip();
    length(field, s, min, max);
    return s;
  }

  public static void length(String field, String value, int min, int max) {
    int n = value == null ? 0 : value.codePointCount(0, value.length());
    if (n < min || n > max)
      throw new UiException(400, "请检查输入", Map.of(field, "请输入 " + min + "–" + max + " 个字符"));
  }

  public static void password(String value) {
    length("password", value, 8, 32);
    if (!value.matches("(?s).*[a-zA-Z].*") || !value.matches("(?s).*[0-9].*"))
      throw new UiException(400, "密码至少包含一个英文字母和一个数字", Map.of("password", "密码至少包含一个英文字母和一个数字"));
  }

  public static long id(String value) {
    try {
      if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
      return Long.parseLong(value);
    } catch (IllegalArgumentException e) {
      throw new UiException(400, "图片标识无效");
    }
  }

  public static String status(String value, boolean optional) {
    if (optional && (value == null || value.isBlank())) return "";
    if ("正常".equals(value)) return "ACTIVE";
    if ("禁用".equals(value)) return "DISABLED";
    throw new UiException(400, "状态无效");
  }
}
