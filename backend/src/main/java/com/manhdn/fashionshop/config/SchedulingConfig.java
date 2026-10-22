package com.manhdn.fashionshop.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật các việc chạy định kỳ (hủy đơn ONLINE quá hạn BR-34, gọi hoàn tiền BR-28). Test tắt bằng
 * app.scheduling.enabled=false để tự gọi các việc này khi cần.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
