package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Order;
import com.manhdn.fashionshop.entity.OrderStatus;
import com.manhdn.fashionshop.entity.PaymentMethod;
import com.manhdn.fashionshop.entity.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chi tiết một đơn hàng (US-11).
 *
 * @param id mã đơn hàng
 * @param status trạng thái (BR-12)
 * @param paymentMethod phương thức thanh toán
 * @param paymentStatus trạng thái thanh toán online, null khi COD
 * @param recipientName người nhận lúc đặt hàng (BR-29)
 * @param phone số điện thoại lúc đặt hàng
 * @param addressDetail địa chỉ lúc đặt hàng
 * @param items các dòng đơn
 * @param subtotal tổng tiền hàng
 * @param shippingFee phí vận chuyển
 * @param discount số tiền giảm giá
 * @param voucherCode mã giảm giá đã dùng, null nếu không dùng
 * @param total tổng thanh toán
 * @param cancellable khách còn tự hủy được không (BR-13)
 * @param createdAt thời điểm đặt hàng
 * @param updatedAt thời điểm đổi trạng thái gần nhất
 * @param paymentDeadline hạn thanh toán online (BR-34), null nếu đơn không còn chờ thanh toán
 * @param paidAt thời điểm thanh toán online thành công, null nếu chưa
 * @param refund trạng thái hoàn tiền (BR-28), null nếu không có
 */
public record OrderResponse(
    long id,
    OrderStatus status,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    String recipientName,
    String phone,
    String addressDetail,
    List<OrderItemResponse> items,
    long subtotal,
    long shippingFee,
    long discount,
    String voucherCode,
    long total,
    boolean cancellable,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime paymentDeadline,
    LocalDateTime paidAt,
    RefundResponse refund) {

  /** Chuyển entity sang DTO. */
  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.getId(),
        order.getStatus(),
        order.getPaymentMethod(),
        order.getPaymentStatus(),
        order.getRecipientNameSnapshot(),
        order.getPhoneSnapshot(),
        order.getAddressDetailSnapshot(),
        order.getItems().stream().map(OrderItemResponse::from).toList(),
        order.getSubtotalAmount(),
        order.getShippingFee(),
        order.getDiscountAmount(),
        order.getVoucher() == null ? null : order.getVoucher().getCode(),
        order.getTotalAmount(),
        order.getStatus().customerCancellable(),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.awaitingPayment() ? order.paymentDeadline() : null,
        order.getPaidAt(),
        RefundResponse.from(order.getRefund()));
  }
}
