package com.manhdn.fashionshop.client;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Gửi email chữ thường qua SMTP (vd Gmail: MAIL_HOST=smtp.gmail.com, MAIL_USERNAME, MAIL_PASSWORD
 * là mật khẩu ứng dụng). Gửi trong luồng riêng: request trả về ngay, thời gian phản hồi không lộ
 * email có tài khoản hay không, và máy chủ email chậm không làm chậm request.
 *
 * <p>Để trống MAIL_HOST thì không gửi gì, chỉ ghi log cảnh báo; đặt thêm MAIL_LOG_LINKS=true (chỉ
 * dùng trên máy dev) thì ghi cả nội dung email vào log để lấy liên kết khi chưa có SMTP.
 */
@Component
public class EmailClient {

  private static final Logger log = LoggerFactory.getLogger(EmailClient.class);

  private final ObjectProvider<JavaMailSender> mailSender;
  private final boolean enabled;
  private final String from;
  private final boolean logContent;
  private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

  /** Đọc cấu hình SMTP; JavaMailSender chỉ có khi spring.mail.host được đặt. */
  public EmailClient(
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${spring.mail.host:}") String host,
      @Value("${app.mail.from:}") String from,
      @Value("${app.mail.log-links:false}") boolean logContent) {
    this.mailSender = mailSender;
    this.enabled = StringUtils.hasText(host);
    this.from = from;
    this.logContent = logContent;
  }

  /** Có cấu hình máy chủ gửi email hay không. */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Gửi email trong luồng nền; lỗi chỉ ghi log, không ném ra cho người gọi.
   *
   * @param to địa chỉ nhận
   * @param subject tiêu đề
   * @param text nội dung chữ thường
   */
  public void sendAsync(String to, String subject, String text) {
    if (!enabled) {
      if (logContent) {
        log.info("Chưa cấu hình MAIL_HOST, email tới {} không được gửi:\n{}", to, text);
      } else {
        log.warn("Chưa cấu hình MAIL_HOST: không gửi được email \"{}\" tới {}", subject, to);
      }
      return;
    }
    executor.execute(() -> send(to, subject, text));
  }

  private void send(String to, String subject, String text) {
    SimpleMailMessage message = new SimpleMailMessage();
    if (StringUtils.hasText(from)) {
      message.setFrom(from);
    }
    message.setTo(to);
    message.setSubject(subject);
    message.setText(text);
    try {
      mailSender.getObject().send(message);
      log.info("Đã gửi email \"{}\" tới {}", subject, to);
    } catch (MailException ex) {
      log.error("Không gửi được email \"{}\" tới {}: {}", subject, to, ex.getMessage());
    }
  }
}
