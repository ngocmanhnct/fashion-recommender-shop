package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.ProductVariant;

/**
 * Biến thể trả về cho client.
 *
 * @param id mã biến thể
 * @param option1Value giá trị ở tầng phân loại 1, null nếu sản phẩm không có phân loại
 * @param option2Value giá trị ở tầng phân loại 2, null nếu sản phẩm có ít hơn 2 tầng
 * @param price giá, VNĐ
 * @param stock tồn kho
 * @param skuCode mã SKU, có thể null
 */
public record VariantResponse(
    Long id, String option1Value, String option2Value, Long price, Integer stock, String skuCode) {

  /** Chuyển entity sang DTO. */
  public static VariantResponse from(ProductVariant v) {
    return new VariantResponse(
        v.getId(),
        v.getOption1Value(),
        v.getOption2Value(),
        v.getPrice(),
        v.getStock(),
        v.getSkuCode());
  }
}
