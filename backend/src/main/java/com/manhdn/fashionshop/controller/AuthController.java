package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.AccountResponse;
import com.manhdn.fashionshop.dto.AuthResponse;
import com.manhdn.fashionshop.dto.ChangePasswordRequest;
import com.manhdn.fashionshop.dto.ForgotPasswordRequest;
import com.manhdn.fashionshop.dto.LoginRequest;
import com.manhdn.fashionshop.dto.ProfileRequest;
import com.manhdn.fashionshop.dto.RegisterRequest;
import com.manhdn.fashionshop.dto.ResetPasswordRequest;
import com.manhdn.fashionshop.service.AccountService;
import com.manhdn.fashionshop.service.AuthService;
import com.manhdn.fashionshop.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API đăng ký, đăng nhập, xem và sửa tài khoản đang đăng nhập, đổi mật khẩu và quên mật khẩu
 * (US-04, US-05; chức năng 21).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final AccountService accountService;
  private final PasswordResetService passwordResetService;

  /** Nhận các service qua constructor. */
  public AuthController(
      AuthService authService,
      AccountService accountService,
      PasswordResetService passwordResetService) {
    this.authService = authService;
    this.accountService = accountService;
    this.passwordResetService = passwordResetService;
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

  /**
   * Sửa hồ sơ của tài khoản đang đăng nhập (khách hàng hoặc quản trị viên).
   *
   * @param jwt token của tài khoản
   * @param request họ tên (bắt buộc), giới tính, ngày sinh (tùy chọn, để trống là bỏ)
   * @return 200 kèm thông tin tài khoản sau khi sửa; 400 nếu dữ liệu không hợp lệ; 401 nếu chưa
   *     đăng nhập
   */
  @PutMapping("/me")
  public AccountResponse updateProfile(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
    return accountService.updateProfile(Long.valueOf(jwt.getSubject()), request);
  }

  /**
   * Đổi mật khẩu của tài khoản đang đăng nhập. Trả 204; 400 nếu mật khẩu hiện tại sai hoặc mật khẩu
   * mới không hợp lệ; 429 nếu nhập sai quá nhiều lần.
   *
   * @param jwt token của tài khoản
   * @param request mật khẩu hiện tại và mật khẩu mới (BR-02)
   */
  @PutMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
    accountService.changePassword(Long.valueOf(jwt.getSubject()), request);
  }

  /**
   * Xin liên kết đặt lại mật khẩu qua email (BR-48). Không cần đăng nhập. Trả 202 dù email có tài
   * khoản hay không (không lộ email nào đã đăng ký); 400 nếu email sai định dạng.
   *
   * @param request email của tài khoản
   */
  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    passwordResetService.requestReset(request);
  }

  /**
   * Đặt mật khẩu mới bằng liên kết trong email (BR-48). Không cần đăng nhập. Trả 204; 400 nếu liên
   * kết không hợp lệ, đã dùng, đã hết hạn hoặc mật khẩu không hợp lệ.
   *
   * @param request chuỗi trong liên kết và mật khẩu mới (BR-02)
   */
  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    passwordResetService.resetPassword(request);
  }
}
