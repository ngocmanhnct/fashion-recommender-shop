package com.manhdn.fashionshop.exception;

/** Email đăng ký đã thuộc về một tài khoản khác (BR-01). Trả về HTTP 409. */
public class EmailAlreadyExistsException extends RuntimeException {

  /** Tạo exception với thông báo hiển thị cho người dùng. */
  public EmailAlreadyExistsException() {
    super("Email đã được sử dụng cho một tài khoản khác");
  }
}
