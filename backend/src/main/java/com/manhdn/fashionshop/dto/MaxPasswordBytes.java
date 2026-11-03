package com.manhdn.fashionshop.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Mật khẩu không quá 72 byte UTF-8. BCrypt chỉ băm được tới 72 byte, còn {@code @Size} đếm ký tự:
 * chữ có dấu chiếm 2-3 byte, nên mật khẩu 31 ký tự có dấu đã có thể vượt và làm lỗi 500.
 */
@Documented
@Constraint(validatedBy = MaxPasswordBytes.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxPasswordBytes {

  /** Số byte tối đa BCrypt nhận. */
  int MAX_BYTES = 72;

  /** Thông báo lỗi. */
  String message() default "Mật khẩu quá dài: tối đa 72 byte (chữ có dấu tính 2-3 byte)";

  /** Nhóm kiểm tra. */
  Class<?>[] groups() default {};

  /** Dữ liệu kèm theo. */
  Class<? extends Payload>[] payload() default {};

  /** Đếm số byte UTF-8 của mật khẩu. */
  class Validator implements ConstraintValidator<MaxPasswordBytes, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return value == null || value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
  }
}
