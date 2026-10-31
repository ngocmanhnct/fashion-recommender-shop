package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.AccountResponse;
import com.manhdn.fashionshop.dto.ChangePasswordRequest;
import com.manhdn.fashionshop.dto.ProfileRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.PasswordResetTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hồ sơ cá nhân và đổi mật khẩu của tài khoản đang đăng nhập (chức năng 21; BR-02, BR-03). */
@Service
public class AccountService {

  private final AccountRepository accountRepository;
  private final PasswordResetTokenRepository resetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttemptService;
  private final Clock clock;

  /** Nhận các thành phần cần thiết qua constructor. */
  public AccountService(
      AccountRepository accountRepository,
      PasswordResetTokenRepository resetTokenRepository,
      PasswordEncoder passwordEncoder,
      LoginAttemptService loginAttemptService,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.resetTokenRepository = resetTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.loginAttemptService = loginAttemptService;
    this.clock = clock;
  }

  /**
   * Sửa họ tên, giới tính, ngày sinh. Giới tính, ngày sinh để trống là bỏ thông tin đã khai (vẫn là
   * trường tùy chọn, BR-03). Email và vai trò không đổi.
   *
   * @param accountId tài khoản đang đăng nhập
   * @param request hồ sơ mới
   * @return thông tin tài khoản sau khi sửa
   */
  @Transactional
  public AccountResponse updateProfile(Long accountId, ProfileRequest request) {
    Account account = find(accountId);
    account.setFullName(TextNormalizer.nfc(request.fullName().trim()));
    account.setGender(request.gender());
    account.setBirthdate(request.birthdate());
    accountRepository.flush();
    return AccountResponse.from(account);
  }

  /**
   * Đổi mật khẩu khi biết mật khẩu hiện tại. Nhập sai mật khẩu hiện tại được tính như đăng nhập sai
   * (5 lần trong 15 phút thì khóa 15 phút), để người cầm máy đang đăng nhập không dò được mật khẩu.
   * Đổi xong thì mọi liên kết đặt lại mật khẩu chưa dùng của tài khoản hết hiệu lực.
   *
   * @param accountId tài khoản đang đăng nhập
   * @param request mật khẩu hiện tại và mật khẩu mới
   * @throws InvalidRequestException nếu mật khẩu hiện tại sai hoặc mật khẩu mới trùng mật khẩu cũ
   */
  @Transactional
  public void changePassword(Long accountId, ChangePasswordRequest request) {
    Account account = find(accountId);
    loginAttemptService.checkNotLocked(account.getEmail());
    if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
      loginAttemptService.recordFailure(account.getEmail());
      throw new InvalidRequestException("Mật khẩu hiện tại không đúng");
    }
    if (request.newPassword().equals(request.currentPassword())) {
      throw new InvalidRequestException("Mật khẩu mới phải khác mật khẩu hiện tại");
    }
    account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    resetTokenRepository.markAllUsed(account.getId(), LocalDateTime.now(clock));
    loginAttemptService.recordSuccess(account.getEmail());
  }

  private Account find(Long accountId) {
    return accountRepository
        .findById(accountId)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản"));
  }
}
