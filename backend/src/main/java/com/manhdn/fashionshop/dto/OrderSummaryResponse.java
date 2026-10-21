package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Order;
import com.manhdn.fashionshop.entity.OrderItem;
import com.manhdn.fashionshop.entity.OrderStatus;
import com.manhdn.fashionshop.entity.PaymentMethod;
import java.time.LocalDateTime;

/**
 * Một đơn trong danh sách đơn hàng của khách (US-11).
 *
 * @param id mã đơn hàng
 * @param status trạng thái
 * @param paymentMethod phương thức thanh toán
 * @param firstProductName tên sản phẩm của dòng đầu tiên, để khách nhận ra đơn
 * @param itemCount số dòng (sản phẩm khác nhau) trong đơn
 * @param totalQuantity tổng số lượng sản phẩm
 * @param total tổng thanh toán
 * @param createdAt thời điểm đặt hàng
 */
public record OrderSummaryResponse(
    long id,
    OrderStatus status,
    PaymentMethod paymentMethod,
    String firstProductName,
    int itemCount,
    int totalQuantity,
    long total,
    LocalDateTime createdAt) {

  /** Chuyển entity sang DTO. */
  public static OrderSummaryResponse from(Order order) {
    return new OrderSummaryResponse(
        order.getId(),
        order.getStatus(),
        order.getPaymentMethod(),
        order.getItems().isEmpty() ? null : order.getItems().get(0).getProductNameSnapshot(),
        order.getItems().size(),
        order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
        order.getTotalAmount(),
        order.getCreatedAt());
  }
}
