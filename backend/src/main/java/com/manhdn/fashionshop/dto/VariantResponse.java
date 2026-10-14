package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.ProductVariant;

/**
 * Biến thể trả về cho client.
 *
 * @param id mã biến thể
 * @param size kích cỡ
 * @param color màu
 * @param price giá, VNĐ
 * @param stock tồn kho
 * @param skuCode mã SKU, có thể null
 */
public record VariantResponse(
    Long id, String size, String color, Long price, Integer stock, String skuCode) {

  /** Chuyển entity sang DTO. */
  public static VariantResponse from(ProductVariant v) {
    return new VariantResponse(
        v.getId(), v.getSize(), v.getColor(), v.getPrice(), v.getStock(), v.getSkuCode());
  }
}
