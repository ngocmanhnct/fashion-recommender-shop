package com.manhdn.fashionshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Điểm khởi động ứng dụng Spring Boot. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FashionshopApplication {

  /**
   * Chạy ứng dụng.
   *
   * <p>Bật cho Java tự tải chứng chỉ trung gian còn thiếu (theo trường AIA của chứng chỉ): máy chủ
   * sandbox VNPay không gửi kèm chứng chỉ trung gian ZeroSSL, nên khi gọi API hoàn tiền Java báo
   * "PKIX path building failed" dù trình duyệt vẫn mở được. Phải đặt trước khi có kết nối HTTPS đầu
   * tiên, vì Java chỉ đọc thuộc tính này một lần.
   */
  public static void main(String[] args) {
    if (System.getProperty("com.sun.security.enableAIAcaIssuers") == null) {
      System.setProperty("com.sun.security.enableAIAcaIssuers", "true");
    }
    SpringApplication.run(FashionshopApplication.class, args);
  }
}
