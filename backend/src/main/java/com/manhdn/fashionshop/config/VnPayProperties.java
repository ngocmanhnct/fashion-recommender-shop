package com.manhdn.fashionshop.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình cổng thanh toán VNPay (US-10, BR-28), tiền tố app.vnpay. Mã website và khóa bí mật chỉ
 * đặt ở .env (VNPAY_TMN_CODE, VNPAY_HASH_SECRET), không bao giờ ghi vào file trong git.
 *
 * @param tmnCode mã website (vnp_TmnCode) VNPay cấp; trống là chưa bật thanh toán online
 * @param hashSecret khóa bí mật để ký và kiểm tra chữ ký HMAC-SHA512
 * @param payUrl địa chỉ trang thanh toán của VNPay
 * @param apiUrl địa chỉ API truy vấn, hoàn tiền của VNPay
 * @param returnUrl trang của frontend mà VNPay chuyển khách về sau khi thanh toán
 * @param linkTtl thời gian hiệu lực của một đường dẫn thanh toán
 */
@ConfigurationProperties("app.vnpay")
public record VnPayProperties(
    @DefaultValue("") String tmnCode,
    @DefaultValue("") String hashSecret,
    @DefaultValue("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html") String payUrl,
    @DefaultValue("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction") String apiUrl,
    @DefaultValue("http://localhost:5173/payment/vnpay-return") String returnUrl,
    @DefaultValue("15m") Duration linkTtl) {

  /** Đã có đủ mã website và khóa bí mật để dùng VNPay chưa. */
  public boolean configured() {
    return !tmnCode.isBlank() && !hashSecret.isBlank();
  }
}
