package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.AccountResponse;
import com.manhdn.fashionshop.dto.AuthResponse;
import com.manhdn.fashionshop.dto.LoginRequest;
import com.manhdn.fashionshop.dto.RegisterRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.exception.EmailAlreadyExistsException;
import com.manhdn.fashionshop.exception.InvalidCredentialsException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.security.JwtService;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ đăng ký, đăng nhập (US-04, US-05; QĐ_KH4, QĐ_KH5; BR-01 đến BR-04). */
@Service
public class AuthService {

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LoginAttemptService loginAttemptService;

  /**
   * Hash BCrypt của một chuỗi ngẫu nhiên, dùng để vẫn chạy BCrypt khi email không tồn tại: thời
   * gian phản hồi của email có thật và email không tồn tại gần như nhau, không đo được để dò email.
   */
  private final String dummyHash;

  /** Nhận các thành phần cần thiết qua constructor. */
  public AuthService(
      AccountRepository accountRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      LoginAttemptService loginAttemptService) {
    this.accountRepository = accountRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.loginAttemptService = loginAttemptService;
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /**
   * Đăng ký tài khoản mới rồi tự đăng nhập (QĐ_KH4).
   *
   * <p>Vai trò luôn là CUSTOMER, không lấy từ request (BR-04). Email chuẩn hóa về chữ thường để
   * "A@x.vn" và "a@x.vn" là một tài khoản. Email trùng bị chặn ở 2 lớp: kiểm tra trước để báo lỗi
   * rõ ràng, và ràng buộc UNIQUE trong CSDL (BR-01) bắt trường hợp 2 request đồng thời cùng lọt qua
   * bước kiểm tra.
   *
   * <p>Gộp giỏ hàng trình duyệt (BR-07) và chuyển sở thích khảo sát sẽ thêm khi làm giỏ hàng và
   * khảo sát.
   *
   * @param request dữ liệu form đăng ký đã qua validate
   * @return token và thông tin tài khoản vừa tạo
   * @throws EmailAlreadyExistsException nếu email đã có tài khoản
   */
  @Transactional
  public AuthResponse register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    if (accountRepository.existsByEmail(email)) {
      throw new EmailAlreadyExistsException();
    }
    Account account =
        new Account(
            email,
            passwordEncoder.encode(request.password()),
            request.fullName().trim(),
            Role.CUSTOMER);
    account.setGender(request.gender());
    account.setBirthdate(request.birthdate());
    try {
      account = accountRepository.saveAndFlush(account);
    } catch (DataIntegrityViolationException ex) {
      throw new EmailAlreadyExistsException();
    }
    return toAuthResponse(account);
  }

  /**
   * Đăng nhập bằng email và mật khẩu (QĐ_KH5).
   *
   * <p>Sai email hay sai mật khẩu đều báo cùng một lỗi. Email đang bị khóa do sai quá nhiều lần thì
   * từ chối ngay, kể cả khi mật khẩu đúng.
   *
   * @param request email và mật khẩu
   * @return token và thông tin tài khoản
   * @throws InvalidCredentialsException nếu sai email hoặc mật khẩu
   * @throws com.manhdn.fashionshop.exception.TooManyLoginAttemptsException nếu email đang bị khóa
   */
  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    String email = normalizeEmail(request.email());
    loginAttemptService.checkNotLocked(email);
    Optional<Account> account = accountRepository.findByEmail(email);
    String hash = account.map(Account::getPasswordHash).orElse(dummyHash);
    boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
    if (account.isEmpty() || !passwordMatches) {
      loginAttemptService.recordFailure(email);
      throw new InvalidCredentialsException();
    }
    loginAttemptService.recordSuccess(email);
    return toAuthResponse(account.get());
  }

  /**
   * Lấy thông tin tài khoản đang đăng nhập.
   *
   * @param accountId mã tài khoản lấy từ claim sub của JWT
   * @return thông tin tài khoản
   * @throws NoSuchElementException nếu tài khoản không còn tồn tại
   */
  @Transactional(readOnly = true)
  public AccountResponse getAccount(Long accountId) {
    return accountRepository
        .findById(accountId)
        .map(AccountResponse::from)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản"));
  }

  private AuthResponse toAuthResponse(Account account) {
    return new AuthResponse(
        jwtService.issueToken(account),
        "Bearer",
        jwtService.getExpirationSeconds(),
        AccountResponse.from(account));
  }

  /** Bỏ khoảng trắng hai đầu và đổi về chữ thường. */
  static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
