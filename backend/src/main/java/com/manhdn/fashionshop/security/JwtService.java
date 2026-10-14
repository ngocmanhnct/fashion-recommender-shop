package com.manhdn.fashionshop.security;

import com.manhdn.fashionshop.entity.Account;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Cấp JWT sau khi đăng ký hoặc đăng nhập thành công (Bảng 2.8 mục 2): claim {@code sub} là
 * account_id, claim {@code role} là vai trò, hết hạn sau {@code app.jwt.expiration} (24 giờ).
 */
@Service
public class JwtService {

  /** Tên claim chứa vai trò, cũng là claim SecurityConfig đọc để phân quyền. */
  public static final String ROLE_CLAIM = "role";

  private static final String ISSUER = "fashionshop";

  private final JwtEncoder encoder;
  private final Duration expiration;
  private final Clock clock;

  /** Nhận bộ mã hóa JWT, thời hạn token và đồng hồ. */
  public JwtService(
      JwtEncoder encoder, @Value("${app.jwt.expiration:24h}") Duration expiration, Clock clock) {
    this.encoder = encoder;
    this.expiration = expiration;
    this.clock = clock;
  }

  /**
   * Tạo token cho tài khoản.
   *
   * @param account tài khoản đã xác thực
   * @return chuỗi JWT đã ký HS256
   */
  public String issueToken(Account account) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(String.valueOf(account.getId()))
            .issuedAt(now)
            .expiresAt(now.plus(expiration))
            .claim(ROLE_CLAIM, account.getRole().name())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /** Thời hạn token, tính bằng giây. */
  public long getExpirationSeconds() {
    return expiration.toSeconds();
  }
}
