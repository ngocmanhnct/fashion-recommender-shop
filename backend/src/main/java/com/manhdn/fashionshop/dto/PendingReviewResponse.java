package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.OrderItem;
import com.manhdn.fashionshop.entity.Product;
import java.time.LocalDateTime;

/**
 * Một sản phẩm khách đã nhận (đơn Đã giao) nhưng chưa đánh giá.
 *
 * @param productId mã sản phẩm
 * @param productName tên sản phẩm hiện tại
 * @param categoryName tên danh mục, để hiện ảnh minh họa
 * @param variantLabel phân loại đã mua ở đơn Đã giao mới nhất, null nếu không có phân loại
 * @param orderId mã đơn Đã giao mới nhất có sản phẩm này
 * @param orderedAt lúc đặt đơn đó
 */
public record PendingReviewResponse(
    long productId,
    String productName,
    String categoryName,
    String variantLabel,
    long orderId,
    LocalDateTime orderedAt) {

  /** Chuyển dòng đơn (đã nạp đơn, biến thể, sản phẩm, danh mục) sang DTO. */
  public static PendingReviewResponse from(OrderItem item) {
    Product product = item.getVariant().getProduct();
    return new PendingReviewResponse(
        product.getId(),
        product.getName(),
        product.getCategory().getName(),
        item.getVariantLabelSnapshot(),
        item.getOrder().getId(),
        item.getOrder().getCreatedAt());
  }
}
