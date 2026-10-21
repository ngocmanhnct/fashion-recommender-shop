package com.manhdn.fashionshop.entity;

/** Phương thức thanh toán của đơn hàng (US-10), khớp cột orders.payment_method. */
public enum PaymentMethod {
  /** Thanh toán online qua cổng thanh toán. */
  ONLINE,
  /** Thanh toán khi nhận hàng. */
  COD
}
