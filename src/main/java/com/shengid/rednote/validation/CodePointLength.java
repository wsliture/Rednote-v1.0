package com.shengid.rednote.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CodePointLength.Validator.class)
public @interface CodePointLength {
  int min() default 0;

  int max();

  boolean strip() default true;

  String message() default "字符长度不符合要求";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<CodePointLength, String> {
    private int min, max;
    private boolean strip;

    public void initialize(CodePointLength a) {
      min = a.min();
      max = a.max();
      strip = a.strip();
    }

    public boolean isValid(String s, ConstraintValidatorContext context) {
      if (s == null) return min == 0;
      String v = strip ? s.strip() : s;
      int size = v.codePointCount(0, v.length());
      return size >= min && size <= max;
    }
  }
}
