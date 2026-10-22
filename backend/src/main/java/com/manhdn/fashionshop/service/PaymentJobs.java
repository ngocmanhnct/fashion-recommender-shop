package com.manhdn.fashionshop.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Việc chạy định kỳ của thanh toán online, chỉ chạy khi bật lịch (xem SchedulingConfig): hủy đơn
 * ONLINE quá hạn thanh toán (BR-34) và gọi VNPay cho các lần hoàn tiền đang chờ (BR-28).
 */
@Component
public class PaymentJobs {

  private final PaymentService paymentService;
  private final RefundService refundService;

  /** Nhận các service qua constructor. */
  public PaymentJobs(PaymentService paymentService, RefundService refundService) {
    this.paymentService = paymentService;
    this.refundService = refundService;
  }

  /** Quét đơn ONLINE quá hạn thanh toán, mặc định mỗi 5 phút (BR-34). */
  @Scheduled(
      initialDelayString = "${app.vnpay.expiry-scan-interval:5m}",
      fixedDelayString = "${app.vnpay.expiry-scan-interval:5m}")
  public void cancelExpiredOnlineOrders() {
    paymentService.cancelExpiredOnlineOrders();
  }

  /** Gọi VNPay cho các lần hoàn tiền đang chờ, mặc định mỗi phút (BR-28). */
  @Scheduled(
      initialDelayString = "${app.vnpay.refund-interval:1m}",
      fixedDelayString = "${app.vnpay.refund-interval:1m}")
  public void processRefunds() {
    refundService.processPending();
  }
}
