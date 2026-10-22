package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Order;
import com.manhdn.fashionshop.entity.OrderItem;
import com.manhdn.fashionshop.entity.OrderStatus;
import com.manhdn.fashionshop.entity.PaymentMethod;
import com.manhdn.fashionshop.entity.PaymentStatus;
import com.manhdn.fashionshop.entity.RefundStatus;
import java.time.LocalDateTime;

/**
 * Một đơn trong danh sách đơn hàng của trang quản trị (US-17).
 *
 * @param id mã đơn hàng
 * @param status trạng thái
 * @param paymentMethod phương thức thanh toán
 * @param paymentStatus trạng thái thanh toán online, null khi COD
 * @param customerName họ tên tài khoản đặt hàng
 * @param customerEmail email tài khoản đặt hàng
 * @param recipientName người nhận lúc đặt hàng
 * @param phone số điện thoại nhận hàng
 * @param totalQuantity tổng số lượng sản phẩm
 * @param total tổng thanh toán
 * @param createdAt thời điểm đặt hàng
 * @param refundStatus trạng thái hoàn tiền (BR-28), null nếu không có
 */
public record AdminOrderSummaryResponse(
    long id,
    OrderStatus status,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    String customerName,
    String customerEmail,
    String recipientName,
    String phone,
    int totalQuantity,
    long total,
    LocalDateTime createdAt,
    RefundStatus refundStatus) {

  /** Chuyển entity sang DTO. */
  public static AdminOrderSummaryResponse from(Order order) {
    return new AdminOrderSummaryResponse(
        order.getId(),
        order.getStatus(),
        order.getPaymentMethod(),
        order.getPaymentStatus(),
        order.getAccount().getFullName(),
        order.getAccount().getEmail(),
        order.getRecipientNameSnapshot(),
        order.getPhoneSnapshot(),
        order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
        order.getTotalAmount(),
        order.getCreatedAt(),
        order.getRefund() == null ? null : order.getRefund().getStatus());
  }
}
