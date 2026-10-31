package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Đánh giá khách gửi cho một sản phẩm đã nhận hàng (US-15).
 *
 * @param rating số sao từ 1 đến 5
 * @param comment nhận xét, không bắt buộc, tối đa 1.000 ký tự
 */
public record ReviewRequest(
    @NotNull(message = "Hãy chọn số sao")
        @Min(value = 1, message = "Số sao từ 1 đến 5")
        @Max(value = 5, message = "Số sao từ 1 đến 5")
        Integer rating,
    @Size(max = 1000, message = "Nhận xét tối đa 1.000 ký tự") String comment) {}
