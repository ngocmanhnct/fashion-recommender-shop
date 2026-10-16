package com.manhdn.fashionshop.exception;

/**
 * Dữ liệu gửi lên sai quy tắc nghiệp vụ mà annotation validate không kiểm tra được vì phụ thuộc
 * nhiều trường hoặc dữ liệu đã lưu (vd biến thể thiếu giá trị cho tầng phân loại của sản phẩm). Trả
 * về HTTP 400 kèm thông báo cho người dùng.
 */
public class InvalidRequestException extends RuntimeException {

  /** Tạo exception với thông báo hiển thị cho người dùng. */
  public InvalidRequestException(String message) {
    super(message);
  }
}
