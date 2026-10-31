package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Bật hoặc tắt mã giảm giá (US-22).
 *
 * @param active true để bật lại, false để ngừng áp dụng trước hạn
 */
public record VoucherActiveRequest(@NotNull(message = "Thiếu trạng thái bật/tắt") Boolean active) {}
