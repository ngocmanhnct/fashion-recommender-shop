package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.DiscountType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;

/**
 * Tạo hoặc sửa mã giảm giá (US-22, BR-30). Thời hạn theo giờ Việt Nam.
 *
 * @param code mã khách nhập, 3 đến 50 ký tự gồm chữ không dấu, số, dấu - hoặc _; lưu dạng chữ hoa
 * @param discountType giảm theo phần trăm hay số tiền cố định
 * @param discountValue phần trăm (1 đến 100) hoặc số tiền VNĐ
 * @param minOrderAmount tổng tiền hàng tối thiểu để dùng mã, null là không yêu cầu
 * @param maxDiscountAmount trần số tiền giảm cho mã phần trăm, null là không giới hạn
 * @param validFrom bắt đầu áp dụng
 * @param validUntil hết hạn
 * @param usageLimitTotal tổng số lượt dùng, null là không giới hạn
 * @param usageLimitPerAccount số lượt mỗi khách, null là không giới hạn
 */
public record VoucherRequest(
    @NotBlank(message = "Chưa nhập mã")
        @Pattern(
            regexp = "^[A-Za-z0-9_-]{3,50}$",
            message = "Mã gồm 3 đến 50 ký tự: chữ không dấu, số, dấu - hoặc _")
        String code,
    @NotNull(message = "Chưa chọn loại giảm giá") DiscountType discountType,
    @NotNull(message = "Chưa nhập mức giảm")
        @Positive(message = "Mức giảm phải lớn hơn 0")
        @Max(value = 100_000_000, message = "Mức giảm tối đa 100.000.000đ")
        Long discountValue,
    @PositiveOrZero(message = "Đơn tối thiểu không được âm") Long minOrderAmount,
    @Positive(message = "Trần giảm phải lớn hơn 0") Long maxDiscountAmount,
    @NotNull(message = "Chưa chọn thời điểm bắt đầu") LocalDateTime validFrom,
    @NotNull(message = "Chưa chọn thời điểm hết hạn") LocalDateTime validUntil,
    @Positive(message = "Tổng lượt dùng phải lớn hơn 0") Integer usageLimitTotal,
    @Positive(message = "Lượt dùng mỗi khách phải lớn hơn 0") Integer usageLimitPerAccount) {}
