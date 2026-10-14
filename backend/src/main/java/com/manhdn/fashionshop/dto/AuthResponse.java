package com.manhdn.fashionshop.dto;

/**
 * Kết quả đăng ký hoặc đăng nhập thành công.
 *
 * @param accessToken JWT, gửi lại trong header "Authorization: Bearer ..."
 * @param tokenType luôn là "Bearer"
 * @param expiresIn số giây token còn hiệu lực (24 giờ = 86400)
 * @param account thông tin tài khoản vừa đăng nhập
 */
public record AuthResponse(
    String accessToken, String tokenType, long expiresIn, AccountResponse account) {}
