package com.manhdn.fashionshop.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình gọi dịch vụ học máy (ml-service), tiền tố app.ml-service. Backend chỉ gọi các API tính
 * ngay (sản phẩm liên quan, gợi ý theo khảo sát); gợi ý trang chủ đọc từ bảng recommendation.
 *
 * @param enabled có gọi ml-service không; tắt thì luôn dùng gợi ý theo quy tắc (test tắt sẵn)
 * @param baseUrl địa chỉ ml-service, trong Docker Compose là http://ml-service:8000
 * @param timeout thời gian chờ tối đa cho 1 lần gọi (kết nối và đọc); quá hạn thì dùng quy tắc
 */
@ConfigurationProperties("app.ml-service")
public record MlServiceProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("http://localhost:8000") String baseUrl,
    @DefaultValue("500ms") Duration timeout) {}
