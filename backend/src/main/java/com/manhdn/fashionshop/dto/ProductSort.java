package com.manhdn.fashionshop.dto;

/** Cách sắp xếp danh sách sản phẩm của trang người dùng (US-01). */
public enum ProductSort {
  /** Phổ biến (mặc định): bán chạy trước, hòa thì xen kẽ danh mục theo vị trí trong danh mục. */
  POPULAR,
  /** Mới nhất: sản phẩm thêm sau đứng trước. */
  NEWEST,
  /** Giá tăng dần, theo giá thấp nhất của các biến thể. */
  PRICE_ASC,
  /** Giá giảm dần, theo giá thấp nhất của các biến thể. */
  PRICE_DESC
}
