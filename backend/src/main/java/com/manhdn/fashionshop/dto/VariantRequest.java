package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu một biến thể (BR-05).
 *
 * @param size kích cỡ, vd "40" hoặc "Free size"
 * @param color màu
 * @param price giá bán, VNĐ, số nguyên dương (BR-27)
 * @param stock tồn kho, không âm
 * @param skuCode mã SKU, tùy chọn, duy nhất toàn hệ thống
 */
public record VariantRequest(
    @NotBlank(message = "Size không được để trống")
        @Size(max = 20, message = "Size tối đa 20 ký tự")
        String size,
    @NotBlank(message = "Màu không được để trống") @Size(max = 50, message = "Màu tối đa 50 ký tự")
        String color,
    @NotNull(message = "Giá không được để trống") @Positive(message = "Giá phải lớn hơn 0")
        Long price,
    @NotNull(message = "Tồn kho không được để trống")
        @PositiveOrZero(message = "Tồn kho không được âm")
        Integer stock,
    @Size(max = 50, message = "Mã SKU tối đa 50 ký tự") String skuCode) {}
