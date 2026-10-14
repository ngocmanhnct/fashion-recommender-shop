package com.manhdn.fashionshop.exception;

/**
 * Yêu cầu hợp lệ về định dạng nhưng xung đột với dữ liệu hiện có (trùng tên, trùng mã SKU, xóa danh
 * mục còn sản phẩm...). Trả về HTTP 409 kèm thông báo cho người dùng.
 */
public class ConflictException extends RuntimeException {

  /** Tạo exception với thông báo hiển thị cho người dùng. */
  public ConflictException(String message) {
    super(message);
  }
}
