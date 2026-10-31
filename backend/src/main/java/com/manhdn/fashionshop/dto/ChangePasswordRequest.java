package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Đổi mật khẩu khi đang đăng nhập (chức năng 21).
 *
 * @param currentPassword mật khẩu đang dùng
 * @param newPassword mật khẩu mới, cùng quy tắc với lúc đăng ký (BR-02)
 */
public record ChangePasswordRequest(
    @NotBlank(message = "Nhập mật khẩu hiện tại") String currentPassword,
    @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        @Pattern(
            regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
            message = "Mật khẩu phải có ít nhất 1 chữ cái và 1 chữ số")
        String newPassword) {}
