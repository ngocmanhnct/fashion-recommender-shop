package com.manhdn.fashionshop.entity;

/**
 * Loại sự kiện trong nhật ký product_event_log (Mục 3.14), khớp thứ tự giá trị ENUM trong migration
 * V3.
 */
public enum ProductEventType {
  /** Khách hàng đã đăng nhập thêm sản phẩm vào giỏ hàng (US-06). */
  ADD_TO_CART,
  /** Một sản phẩm gợi ý được hiển thị (US-12, US-13; dùng từ Tuần 8). */
  RECOMMENDATION_SHOWN,
  /** Một sản phẩm gợi ý được bấm vào (US-12, US-13; dùng từ Tuần 8). */
  RECOMMENDATION_CLICKED
}
