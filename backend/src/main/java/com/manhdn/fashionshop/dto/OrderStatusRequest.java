package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Yêu cầu đổi trạng thái đơn của quản trị viên (US-17).
 *
 * @param status trạng thái mới: trạng thái kế tiếp hoặc CANCELLED
 */
public record OrderStatusRequest(@NotNull(message = "Chọn trạng thái mới") OrderStatus status) {}
