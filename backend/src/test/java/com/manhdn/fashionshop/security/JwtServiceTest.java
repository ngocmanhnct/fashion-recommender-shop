package com.manhdn.fashionshop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

  private static final String SECRET = "khoa-chi-dung-trong-unit-test-dai-hon-32-byte";

  private final JwtKeyProvider keyProvider = new JwtKeyProvider(SECRET);
  private final JwtDecoder decoder =
      NimbusJwtDecoder.withSecretKey(keyProvider.getKey()).macAlgorithm(MacAlgorithm.HS256).build();

  @Test
  void tokenCarriesAccountIdRoleAnd24HourExpiry() {
    Instant now = Instant.now();
    JwtService service = serviceAt(now);

    Jwt jwt = decoder.decode(service.issueToken(account(42L, Role.ADMIN)));

    assertThat(jwt.getSubject()).isEqualTo("42");
    assertThat(jwt.getClaimAsString(JwtService.ROLE_CLAIM)).isEqualTo("ADMIN");
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofHours(24));
    assertThat(service.getExpirationSeconds()).isEqualTo(86_400);
  }

  @Test
  void expiredTokenIsRejected() {
    JwtService service = serviceAt(Instant.now().minus(Duration.ofHours(25)));
    String token = service.issueToken(account(1L, Role.CUSTOMER));

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void tokenSignedWithOtherKeyIsRejected() {
    JwtKeyProvider otherKey = new JwtKeyProvider("");
    JwtService other =
        new JwtService(
            new NimbusJwtEncoder(new ImmutableSecret<>(otherKey.getKey())),
            Duration.ofHours(24),
            Clock.systemUTC());
    String token = other.issueToken(account(1L, Role.CUSTOMER));

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void secretShorterThan32BytesIsRefused() {
    assertThatThrownBy(() -> new JwtKeyProvider("ngan-qua"))
        .isInstanceOf(IllegalStateException.class);
  }

  private JwtService serviceAt(Instant now) {
    return new JwtService(
        new NimbusJwtEncoder(new ImmutableSecret<>(keyProvider.getKey())),
        Duration.ofHours(24),
        Clock.fixed(now, ZoneOffset.UTC));
  }

  private static Account account(Long id, Role role) {
    Account account = new Account("a@x.vn", "hash", "Nguyễn Văn A", role);
    ReflectionTestUtils.setField(account, "id", id);
    return account;
  }
}
