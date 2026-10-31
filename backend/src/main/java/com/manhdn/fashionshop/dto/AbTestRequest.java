package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bắt đầu một thử nghiệm A/B gợi ý ở trang chủ (US-27).
 *
 * @param name tên để admin phân biệt các lần thử nghiệm
 * @param treatmentPercent phần trăm khách vào nhóm thấy gợi ý của ml-service, 10 đến 90
 */
public record AbTestRequest(
    @NotBlank(message = "Chưa nhập tên thử nghiệm")
        @Size(max = 100, message = "Tên tối đa 100 ký tự")
        String name,
    @NotNull(message = "Chưa nhập tỷ lệ chia nhóm")
        @Min(value = 10, message = "Tỷ lệ nhóm gợi ý từ 10% đến 90%")
        @Max(value = 90, message = "Tỷ lệ nhóm gợi ý từ 10% đến 90%")
        Integer treatmentPercent) {}
