package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.client.EmailClient;
import com.manhdn.fashionshop.dto.ForgotPasswordRequest;
import com.manhdn.fashionshop.dto.ResetPasswordRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.PasswordResetToken;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.PasswordResetTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Quên mật khẩu (chức năng 21, BR-48): gửi liên kết đặt lại mật khẩu qua email, rồi đặt mật khẩu
 * mới bằng liên kết đó.
 *
 * <p>Liên kết chứa 1 chuỗi ngẫu nhiên 256 bit; CSDL chỉ lưu bản băm SHA-256 của chuỗi. Liên kết
 * dùng được 1 lần, hết hạn sau 30 phút; đặt lại hoặc đổi mật khẩu xong thì mọi liên kết khác của
 * tài khoản cũng hết hiệu lực. Xin liên kết luôn trả cùng một kết quả dù email có tài khoản hay
 * không, và email được gửi trong luồng nền, nên không dò được email nào có tài khoản.
 */
@Service
public class PasswordResetService {

  /** Thời hạn của liên kết. */
  public static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

  /** Trong khoảng này sau lần gửi trước, xin thêm cũng không gửi email mới. */
  public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);

  /** Số email đặt lại mật khẩu tối đa mỗi tài khoản trong 1 giờ. */
  public static final int MAX_EMAILS_PER_HOUR = 5;

  static final String SUBJECT = "Đặt lại mật khẩu Fashion Shop";
  static final String INVALID_LINK =
      "Liên kết đặt lại mật khẩu không hợp lệ, đã dùng hoặc đã hết hạn. Hãy xin liên kết mới.";

  private static final int TOKEN_BYTES = 32;

  private final AccountRepository accountRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttemptService;
  private final EmailClient emailClient;
  private final Clock clock;
  private final String frontendUrl;
  private final SecureRandom random = new SecureRandom();

  /** Nhận các thành phần cần thiết qua constructor. */
  public PasswordResetService(
      AccountRepository accountRepository,
      PasswordResetTokenRepository tokenRepository,
      PasswordEncoder passwordEncoder,
      LoginAttemptService loginAttemptService,
      EmailClient emailClient,
      Clock clock,
      @Value("${app.frontend-url}") String frontendUrl) {
    this.accountRepository = accountRepository;
    this.tokenRepository = tokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.loginAttemptService = loginAttemptService;
    this.emailClient = emailClient;
    this.clock = clock;
    this.frontendUrl = frontendUrl.replaceAll("/+$", "");
  }

  /**
   * Tạo liên kết và gửi email nếu email có tài khoản; không có thì không làm gì (người gọi vẫn nhận
   * cùng một phản hồi). Không gửi thêm khi vừa gửi trong 60 giây hoặc đã gửi 5 lần trong 1 giờ, để
   * không bị dùng để gửi thư rác tới hộp thư của người khác.
   *
   * @param request email của tài khoản
   */
  @Transactional
  public void requestReset(ForgotPasswordRequest request) {
    String email = AuthService.normalizeEmail(request.email());
    Optional<Account> found = accountRepository.findByEmail(email);
    if (found.isEmpty()) {
      return;
    }
    Account account = found.get();
    LocalDateTime now = LocalDateTime.now(clock);
    if (tokenRepository.countByAccountIdAndCreatedAtAfter(
                account.getId(), now.minus(RESEND_INTERVAL))
            > 0
        || tokenRepository.countByAccountIdAndCreatedAtAfter(account.getId(), now.minusHours(1))
            >= MAX_EMAILS_PER_HOUR) {
      return;
    }
    String token = newToken();
    tokenRepository.save(
        new PasswordResetToken(account, sha256(token), now, now.plus(TOKEN_LIFETIME)));
    String text =
        emailText(account.getFullName(), email, frontendUrl + "/reset-password?token=" + token);
    // Chỉ gửi khi liên kết đã nằm trong CSDL, để người nhận bấm ngay vẫn dùng được.
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            emailClient.sendAsync(email, SUBJECT, text);
          }
        });
  }

  /**
   * Đặt mật khẩu mới bằng liên kết còn hiệu lực, rồi làm mọi liên kết khác của tài khoản hết hiệu
   * lực và gỡ khóa đăng nhập sai (nếu có) của email.
   *
   * @param request chuỗi trong liên kết và mật khẩu mới
   * @throws InvalidRequestException nếu liên kết không có, đã dùng hoặc đã hết hạn
   */
  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    LocalDateTime now = LocalDateTime.now(clock);
    PasswordResetToken token =
        tokenRepository
            .findForUpdate(sha256(request.token()))
            .filter(t -> t.isUsableAt(now))
            .orElseThrow(() -> new InvalidRequestException(INVALID_LINK));
    Account account = token.getAccount();
    account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    token.setUsedAt(now);
    tokenRepository.markAllUsed(account.getId(), now);
    loginAttemptService.recordSuccess(account.getEmail());
  }

  private String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Bản băm SHA-256 dạng hex 64 ký tự (cột token_hash). */
  static String sha256(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("JVM không có SHA-256", ex);
    }
  }

  private static String emailText(String fullName, String email, String link) {
    return String.join(
            "\n",
            "Xin chào %s,",
            "",
            "Bạn (hoặc ai đó) vừa yêu cầu đặt lại mật khẩu cho tài khoản %s tại Fashion Shop.",
            "Mở liên kết dưới đây để đặt mật khẩu mới:",
            "",
            "%s",
            "",
            "Liên kết dùng được 1 lần và hết hạn sau %d phút. Nếu bạn không yêu cầu, hãy bỏ qua"
                + " email này, mật khẩu của bạn vẫn giữ nguyên.",
            "",
            "Fashion Shop",
            "")
        .formatted(fullName, email, link, TOKEN_LIFETIME.toMinutes());
  }
}
