package com.manhdn.fashionshop.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Khóa bí mật ký JWT (HMAC-SHA256), đọc từ {@code app.jwt.secret} (biến môi trường {@code
 * JWT_SECRET}). Khóa không bao giờ ghi trong file có commit.
 *
 * <p>Để trống (máy dev, CI) thì sinh khóa ngẫu nhiên mỗi lần khởi động và ghi cảnh báo: chạy được
 * ngay, nhưng khởi động lại backend thì mọi token cũ mất hiệu lực. Môi trường thật luôn phải đặt
 * {@code JWT_SECRET}.
 */
@Component
public class JwtKeyProvider {

  /** HS256 yêu cầu khóa tối thiểu 256 bit. */
  static final int MIN_SECRET_BYTES = 32;

  private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

  private final SecretKey key;

  /** Dựng khóa từ cấu hình, hoặc sinh ngẫu nhiên nếu cấu hình để trống. */
  public JwtKeyProvider(@Value("${app.jwt.secret:}") String secret) {
    byte[] bytes;
    if (secret == null || secret.isBlank()) {
      bytes = new byte[MIN_SECRET_BYTES];
      new SecureRandom().nextBytes(bytes);
      log.warn(
          "JWT_SECRET chưa được đặt: dùng khóa ngẫu nhiên, token mất hiệu lực khi khởi động lại."
              + " Môi trường thật phải đặt JWT_SECRET.");
    } else {
      bytes = secret.getBytes(StandardCharsets.UTF_8);
      if (bytes.length < MIN_SECRET_BYTES) {
        throw new IllegalStateException(
            "JWT_SECRET phải dài tối thiểu " + MIN_SECRET_BYTES + " byte (HS256)");
      }
    }
    this.key = new SecretKeySpec(bytes, "HmacSHA256");
  }

  /** Khóa dùng chung cho việc ký và kiểm tra JWT. */
  public SecretKey getKey() {
    return key;
  }
}
