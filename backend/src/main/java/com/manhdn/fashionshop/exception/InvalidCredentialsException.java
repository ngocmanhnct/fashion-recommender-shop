package com.manhdn.fashionshop.exception;

/**
 * Sai email hoặc mật khẩu. Cùng một thông báo cho cả hai trường hợp, để không lộ email nào đã có
 * tài khoản (QĐ_KH5, Bảng 2.8 mục 2). Trả về HTTP 401.
 */
public class InvalidCredentialsException extends RuntimeException {

  /** Tạo exception với thông báo chung. */
  public InvalidCredentialsException() {
    super("Email hoặc mật khẩu không đúng");
  }
}
