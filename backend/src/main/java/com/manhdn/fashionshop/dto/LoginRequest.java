package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Dữ liệu form đăng nhập (US-05).
 *
 * @param email email đăng nhập
 * @param password mật khẩu
 */
public record LoginRequest(
    @NotBlank(message = "Email không được để trống") String email,
    @NotBlank(message = "Mật khẩu không được để trống") String password) {}
