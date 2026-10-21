package com.manhdn.fashionshop.entity;

/** Trạng thái đơn hàng (BR-12), khớp thứ tự giá trị ENUM của cột orders.status. */
public enum OrderStatus {
  /** Chờ xử lý: đơn vừa được tạo. */
  PENDING,
  /** Đang xử lý: cửa hàng đang chuẩn bị hàng. */
  PROCESSING,
  /** Đang giao: đơn đã rời kho, khách không tự hủy được nữa (BR-13). */
  SHIPPING,
  /** Đã giao: đơn hoàn tất. */
  DELIVERED,
  /** Đã hủy: tồn kho đã được hoàn lại (BR-16). */
  CANCELLED;

  /** Khách hàng còn tự hủy được đơn ở trạng thái này không (BR-13). */
  public boolean customerCancellable() {
    return this == PENDING || this == PROCESSING;
  }
}
