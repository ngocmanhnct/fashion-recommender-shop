package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Xin liên kết đặt lại mật khẩu (chức năng 21, BR-48).
 *
 * @param email email của tài khoản
 */
public record ForgotPasswordRequest(
    @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email) {}
