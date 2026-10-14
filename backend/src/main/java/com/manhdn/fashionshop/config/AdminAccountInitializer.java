package com.manhdn.fashionshop.config;

import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Tạo tài khoản Quản trị viên đầu tiên lúc khởi động, vì đăng ký công khai chỉ tạo được Khách hàng
 * (BR-04). Đọc {@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD} từ biến môi trường; để trống thì bỏ qua.
 * Chỉ tạo khi email đó chưa có tài khoản, nên khởi động lại nhiều lần không tạo trùng và không ghi
 * đè mật khẩu đã đổi. Mật khẩu admin không bao giờ nằm trong migration hay file có commit.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final String adminEmail;
  private final String adminPassword;

  /** Nhận repository, bộ băm mật khẩu và thông tin admin từ cấu hình. */
  public AdminAccountInitializer(
      AccountRepository accountRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.admin.email:}") String adminEmail,
      @Value("${app.admin.password:}") String adminPassword) {
    this.accountRepository = accountRepository;
    this.passwordEncoder = passwordEncoder;
    this.adminEmail = adminEmail;
    this.adminPassword = adminPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (adminEmail.isBlank() || adminPassword.isBlank()) {
      return;
    }
    String email = adminEmail.trim().toLowerCase(Locale.ROOT);
    if (accountRepository.existsByEmail(email)) {
      return;
    }
    accountRepository.save(
        new Account(email, passwordEncoder.encode(adminPassword), "Quản trị viên", Role.ADMIN));
    log.info("Đã tạo tài khoản Quản trị viên {}", email);
  }
}
