package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Brand;

/**
 * Thương hiệu trả về cho client.
 *
 * @param id mã thương hiệu
 * @param name tên thương hiệu
 */
public record BrandResponse(Long id, String name) {

  /** Chuyển entity sang DTO; trả null nếu sản phẩm không gắn thương hiệu. */
  public static BrandResponse from(Brand brand) {
    return brand == null ? null : new BrandResponse(brand.getId(), brand.getName());
  }
}
