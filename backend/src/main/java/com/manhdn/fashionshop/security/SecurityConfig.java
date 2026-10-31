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
 *   <li>API đọc sản phẩm, danh mục, thương hiệu công khai (US-01, US-02, US-03); ghi lượt xem sản
 *       phẩm chỉ cho CUSTOMER (US-03). Giỏ hàng chỉ cho CUSTOMER, trừ xem trước giỏ hàng của khách
 *       chưa đăng nhập (US-06, BR-06). Danh sách yêu thích chỉ cho CUSTOMER (US-14). Xem đánh giá
 *       công khai, gửi đánh giá chỉ cho CUSTOMER (US-15). Token sai hoặc hết hạn vẫn bị 401 kể cả ở
 *       API công khai.
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
                auth.requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/forgot-password",
                        "/api/auth/reset-password")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/categories",
                        "/api/brands",
                        "/api/products",
                        "/api/products/*",
                        "/api/products/*/related")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/products/*/views")
                    .hasRole("CUSTOMER")
                    .requestMatchers("/api/products/*/reviews/me")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/api/products/*/reviews")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/cart/preview")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/survey")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/survey/brands", "/api/survey/evaluate")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/recommendations/events")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/payments/vnpay/return", "/api/payments/vnpay/ipn")
                    .permitAll()
                    .requestMatchers(
                        "/api/cart",
                        "/api/cart/**",
                        "/api/addresses",
                        "/api/addresses/**",
                        "/api/orders",
                        "/api/orders/**",
                        "/api/survey/me",
                        "/api/survey/me/**",
                        "/api/recommendations/me",
                        "/api/wishlist",
                        "/api/wishlist/**",
                        "/api/vouchers/me")
                    .hasRole("CUSTOMER")
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
