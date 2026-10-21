package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Thêm hoặc sửa một địa chỉ trong sổ địa chỉ (US-09).
 *
 * @param recipientName tên người nhận
 * @param phone số điện thoại, 10 hoặc 11 chữ số bắt đầu bằng 0, không có khoảng trắng
 * @param addressDetail địa chỉ chi tiết (số nhà, đường, phường/xã, tỉnh/thành)
 * @param isDefault true thì đặt làm địa chỉ mặc định; false không bỏ mặc định của địa chỉ đang là
 *     mặc định (mỗi sổ luôn có đúng 1 địa chỉ mặc định)
 */
public record AddressRequest(
    @NotBlank(message = "Tên người nhận không được để trống")
        @Size(max = 255, message = "Tên người nhận tối đa 255 ký tự")
        String recipientName,
    @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(
            regexp = "0\\d{9,10}",
            message = "Số điện thoại gồm 10 hoặc 11 chữ số và bắt đầu bằng 0")
        String phone,
    @NotBlank(message = "Địa chỉ không được để trống")
        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String addressDetail,
    @NotNull(message = "Chưa chọn có đặt làm địa chỉ mặc định không") Boolean isDefault) {}
