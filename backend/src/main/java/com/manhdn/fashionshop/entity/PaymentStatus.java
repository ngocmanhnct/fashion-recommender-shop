package com.manhdn.fashionshop.entity;

/** Trạng thái thanh toán online (US-10, BR-34), khớp cột orders.payment_status; NULL khi COD. */
public enum PaymentStatus {
  /** Đang chờ cổng thanh toán xác nhận. */
  PENDING,
  /** Cổng thanh toán đã xác nhận. */
  PAID,
  /** Bỏ ngang hoặc thất bại. */
  FAILED
}
