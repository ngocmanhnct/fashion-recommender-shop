package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu một biến thể (BR-05). Số giá trị phân loại phải khớp số tầng của sản phẩm: không có tầng
 * thì để trống cả 2, có 1 tầng thì chỉ nhập option1Value, có 2 tầng thì nhập cả 2 (service kiểm
 * tra, vì phụ thuộc sản phẩm).
 *
 * @param option1Value giá trị ở tầng phân loại 1, vd "Đen"
 * @param option2Value giá trị ở tầng phân loại 2, vd "42"
 * @param price giá bán, VNĐ, số nguyên dương (BR-27)
 * @param stock tồn kho, không âm
 * @param skuCode mã SKU, tùy chọn, duy nhất toàn hệ thống
 */
public record VariantRequest(
    @Size(max = 100, message = "Giá trị phân loại tối đa 100 ký tự") String option1Value,
    @Size(max = 100, message = "Giá trị phân loại tối đa 100 ký tự") String option2Value,
    @NotNull(message = "Giá không được để trống") @Positive(message = "Giá phải lớn hơn 0")
        Long price,
    @NotNull(message = "Tồn kho không được để trống")
        @PositiveOrZero(message = "Tồn kho không được âm")
        Integer stock,
    @Size(max = 50, message = "Mã SKU tối đa 50 ký tự") String skuCode) {}
