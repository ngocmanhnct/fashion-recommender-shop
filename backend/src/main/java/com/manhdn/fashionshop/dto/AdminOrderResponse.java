package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Order;
import com.manhdn.fashionshop.entity.OrderStatus;

/**
 * Chi tiết một đơn cho trang quản trị (US-17).
 *
 * @param order chi tiết đơn, giống trang của khách
 * @param customerId mã tài khoản đặt hàng
 * @param customerName họ tên tài khoản đặt hàng
 * @param customerEmail email tài khoản đặt hàng
 * @param nextStatus trạng thái kế tiếp quản trị viên chuyển được, null nếu không còn
 * @param adminCancellable quản trị viên còn hủy được đơn không
 */
public record AdminOrderResponse(
    OrderResponse order,
    long customerId,
    String customerName,
    String customerEmail,
    OrderStatus nextStatus,
    boolean adminCancellable) {

  /** Chuyển entity sang DTO. */
  public static AdminOrderResponse from(Order order) {
    return new AdminOrderResponse(
        OrderResponse.from(order),
        order.getAccount().getId(),
        order.getAccount().getFullName(),
        order.getAccount().getEmail(),
        order.getStatus().next(),
        order.getStatus().adminCancellable());
  }
}
