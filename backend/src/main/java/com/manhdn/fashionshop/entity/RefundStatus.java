package com.manhdn.fashionshop.entity;

/** Trạng thái hoàn tiền (BR-28), tách biệt khỏi trạng thái đơn; khớp cột refund.status. */
public enum RefundStatus {
  /** Đang hoàn tiền: đã khởi tạo, đang gọi hoặc chờ thử lại cổng thanh toán. */
  PROCESSING,
  /** Đã hoàn tiền. */
  COMPLETED,
  /** Hoàn tiền thất bại sau nhiều lần thử, cần quản trị viên xử lý thủ công. */
  FAILED
}
