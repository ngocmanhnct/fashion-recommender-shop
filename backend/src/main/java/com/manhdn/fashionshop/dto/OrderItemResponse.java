package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.OrderItem;

/**
 * Một dòng đơn hàng, hiển thị theo bản sao lúc đặt hàng (BR-15).
 *
 * @param productId mã sản phẩm, để mở lại trang sản phẩm
 * @param variantId mã biến thể
 * @param productName tên sản phẩm lúc đặt hàng
 * @param variantLabel nhãn phân loại lúc đặt hàng, null nếu sản phẩm không có phân loại
 * @param unitPrice đơn giá lúc đặt hàng
 * @param quantity số lượng
 * @param lineTotal thành tiền
 */
public record OrderItemResponse(
    long productId,
    long variantId,
    String productName,
    String variantLabel,
    long unitPrice,
    int quantity,
    long lineTotal) {

  /** Chuyển entity sang DTO. */
  public static OrderItemResponse from(OrderItem item) {
    return new OrderItemResponse(
        item.getVariant().getProduct().getId(),
        item.getVariant().getId(),
        item.getProductNameSnapshot(),
        item.getVariantLabelSnapshot(),
        item.getUnitPriceSnapshot(),
        item.getQuantity(),
        item.getLineTotal());
  }
}
