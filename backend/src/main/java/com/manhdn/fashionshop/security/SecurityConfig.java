package com.manhdn.fashionshop.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình Spring Security (Security Layer, Bảng 2.8 mục 2 và mục 5).
 *
 * <ul>
 *   <li>Không dùng session: mỗi request tự mang JWT trong header {@code Authorization: Bearer}.
 *   <li>Kiểm tra JWT bằng resource server của Spring Security (bộ lọc JWT dựng sẵn, đóng vai
 *       JwtFilter trong sơ đồ thành phần): sai chữ ký hoặc hết hạn thì trả 401.
 *   <li>Phân quyền ở backend theo claim {@code role}: mọi API {@code /api/admin/**} chỉ cho ADMIN,
 *       gọi thẳng API mà không qua giao diện cũng bị chặn (403).
 *   <li>CSRF tắt vì không dùng cookie phiên; CORS dùng cấu hình ở WebConfig.
 * </ul>
 */
@Configuration
public class SecurityConfig {

  /** Chuỗi bộ lọc bảo mật và quy tắc truy cập theo đường dẫn. */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/categories", "/api/brands")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  /** Băm mật khẩu bằng BCrypt, cost factor mặc định 10 (Bảng 2.8 mục 2). */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** Bộ ký JWT bằng khóa HMAC dùng chung. */
  @Bean
  public JwtEncoder jwtEncoder(JwtKeyProvider keyProvider) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(keyProvider.getKey()));
  }

  /** Bộ kiểm tra JWT: đúng chữ ký HS256 và còn hạn. */
  @Bean
  public JwtDecoder jwtDecoder(JwtKeyProvider keyProvider) {
    return NimbusJwtDecoder.withSecretKey(keyProvider.getKey())
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }

  /** Đổi claim "role" (CUSTOMER/ADMIN) thành quyền ROLE_CUSTOMER/ROLE_ADMIN của Spring. */
  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(JwtService.ROLE_CLAIM);
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
