package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Đặt mật khẩu mới bằng liên kết gửi qua email (chức năng 21, BR-48).
 *
 * @param token chuỗi trong liên kết
 * @param newPassword mật khẩu mới, cùng quy tắc với lúc đăng ký (BR-02)
 */
public record ResetPasswordRequest(
    @NotBlank(message = "Thiếu mã trong liên kết") @Size(max = 100, message = "Mã không hợp lệ")
        String token,
    @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        @Pattern(
            regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
            message = "Mật khẩu phải có ít nhất 1 chữ cái và 1 chữ số")
        String newPassword) {}
