package com.manhdn.fashionshop.entity;

/**
 * Phong cách mua sắm xác định qua khảo sát lần đầu (US-23, BR-42), theo mô hình phong cách ra quyết
 * định mua của Sproles và Kendall (1986); riêng {@link #GIFT} theo động cơ mua cho người khác của
 * Arnold và Reynolds (2003).
 */
public enum ShoppingStyle {
  /** Người mua tiết kiệm: ưu tiên giá hợp lý. */
  SAVER("Người mua tiết kiệm"),
  /** Tín đồ thương hiệu: ưu tiên thương hiệu quen. */
  BRAND("Tín đồ thương hiệu"),
  /** Người cầu toàn: ưu tiên chất lượng, đánh giá tốt. */
  QUALITY("Người cầu toàn"),
  /** Người theo xu hướng: ưu tiên món đang được nhiều người mua. */
  TREND("Người theo xu hướng"),
  /** Người tặng quà: chỉ có ở nhánh tìm quà, không lưu vào tài khoản. */
  GIFT("Người tặng quà");

  private final String displayName;

  ShoppingStyle(String displayName) {
    this.displayName = displayName;
  }

  /** Tên hiển thị cho khách hàng. */
  public String displayName() {
    return displayName;
  }
}
