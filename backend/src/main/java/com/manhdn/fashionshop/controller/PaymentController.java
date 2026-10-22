package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.VnPayReturnResponse;
import com.manhdn.fashionshop.service.PaymentService;
import com.manhdn.fashionshop.service.PaymentService.CallbackResult;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nhận kết quả thanh toán từ VNPay (US-10). Không cần đăng nhập: dữ liệu được tin vì có chữ ký
 * HMAC-SHA512 của VNPay, không vì người gọi.
 */
@RestController
@RequestMapping("/api/payments/vnpay")
public class PaymentController {

  private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

  private final PaymentService paymentService;

  /** Nhận PaymentService qua constructor. */
  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  /**
   * Trang trả về: frontend chuyển nguyên các tham số VNPay gắn trên URL sang đây để kiểm tra và ghi
   * nhận kết quả (cần khi chạy trên máy, nơi VNPay không gọi IPN tới được).
   *
   * @param params các tham số vnp_ VNPay gửi kèm
   * @return kết quả cho khách xem
   */
  @GetMapping("/return")
  public VnPayReturnResponse handleReturn(@RequestParam Map<String, String> params) {
    CallbackResult result = paymentService.handleCallback(params);
    return switch (result.outcome()) {
      case PAID, ALREADY_CONFIRMED ->
          new VnPayReturnResponse(result.orderId(), true, "Thanh toán thành công");
      case PAID_AFTER_CANCEL ->
          new VnPayReturnResponse(
              result.orderId(),
              false,
              "Đơn hàng đã bị hủy trước khi thanh toán xong. Cửa hàng sẽ hoàn lại tiền cho bạn.");
      case PAYMENT_FAILED ->
          new VnPayReturnResponse(
              result.orderId(),
              false,
              "Thanh toán chưa thành công. Bạn có thể thanh toán lại trong hạn của đơn hàng.");
      case INVALID_SIGNATURE, ORDER_NOT_FOUND, INVALID_AMOUNT ->
          new VnPayReturnResponse(null, false, "Dữ liệu thanh toán không hợp lệ");
    };
  }

  /**
   * IPN: máy chủ VNPay gọi để báo kết quả giao dịch. Trả mã theo quy định của VNPay: 00 đã nhận, 01
   * không có đơn, 02 đã xác nhận từ trước, 04 sai số tiền, 97 sai chữ ký.
   *
   * @param params các tham số vnp_ VNPay gửi kèm
   * @return RspCode và Message cho VNPay
   */
  @GetMapping("/ipn")
  public Map<String, String> handleIpn(@RequestParam Map<String, String> params) {
    CallbackResult result = paymentService.handleCallback(params);
    // Ghi log để biết VNPay đã gọi IPN thật (trang trả về cũng ghi nhận được kết quả nên không
    // nhìn trạng thái đơn mà phân biệt được).
    log.info("Nhận IPN VNPay: đơn {}, kết quả {}", result.orderId(), result.outcome());
    return switch (result.outcome()) {
      case PAID, PAID_AFTER_CANCEL, PAYMENT_FAILED ->
          Map.of("RspCode", "00", "Message", "Confirm Success");
      case ALREADY_CONFIRMED -> Map.of("RspCode", "02", "Message", "Order already confirmed");
      case ORDER_NOT_FOUND -> Map.of("RspCode", "01", "Message", "Order not found");
      case INVALID_AMOUNT -> Map.of("RspCode", "04", "Message", "Invalid amount");
      case INVALID_SIGNATURE -> Map.of("RspCode", "97", "Message", "Invalid signature");
    };
  }
}
