package com.manhdn.fashionshop.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cung cấp đồng hồ hệ thống dạng bean, để test thay bằng đồng hồ giả khi kiểm tra mốc thời gian.
 */
@Configuration
public class ClockConfig {

  /** Đồng hồ hệ thống. */
  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
