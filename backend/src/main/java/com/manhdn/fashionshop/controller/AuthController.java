package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.AccountResponse;
import com.manhdn.fashionshop.dto.AuthResponse;
import com.manhdn.fashionshop.dto.LoginRequest;
import com.manhdn.fashionshop.dto.RegisterRequest;
import com.manhdn.fashionshop.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API đăng ký, đăng nhập và xem tài khoản đang đăng nhập (US-04, US-05). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  /** Nhận AuthService qua constructor. */
  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * Đăng ký tài khoản Khách hàng và tự đăng nhập.
   *
   * @param request email, mật khẩu, họ tên (bắt buộc); giới tính, ngày sinh (tùy chọn)
   * @return 201 kèm JWT; 400 nếu dữ liệu không hợp lệ; 409 nếu email đã tồn tại
   */
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  /**
   * Đăng nhập bằng email và mật khẩu.
   *
   * @param request email và mật khẩu
   * @return 200 kèm JWT; 401 nếu sai thông tin; 429 nếu email đang bị khóa tạm thời
   */
  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  /**
   * Thông tin tài khoản đang đăng nhập, xác định qua JWT trong header Authorization.
   *
   * @param jwt token đã được Spring Security kiểm tra chữ ký và hạn dùng
   * @return 200 kèm thông tin tài khoản; 401 nếu thiếu hoặc sai token
   */
  @GetMapping("/me")
  public AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
    return authService.getAccount(Long.valueOf(jwt.getSubject()));
  }
}
