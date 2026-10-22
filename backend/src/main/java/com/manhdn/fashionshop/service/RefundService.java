package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.client.VnPayClient;
import com.manhdn.fashionshop.client.VnPayClient.RefundRequest;
import com.manhdn.fashionshop.client.VnPayClient.RefundResult;
import com.manhdn.fashionshop.entity.Order;
import com.manhdn.fashionshop.entity.PaymentStatus;
import com.manhdn.fashionshop.entity.Refund;
import com.manhdn.fashionshop.entity.RefundStatus;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.repository.RefundRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hoàn tiền đơn đã thanh toán online rồi bị hủy (BR-28): khởi tạo trong giao dịch hủy đơn, gọi
 * VNPay ngoài giao dịch, tự thử lại tối đa {@link Refund#MAX_ATTEMPTS} lần rồi chuyển Hoàn tiền
 * thất bại để quản trị viên xử lý thủ công (US-17).
 */
@Service
public class RefundService {

  /** Múi giờ VNPay dùng cho mọi mốc thời gian. */
  public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

  private static final Logger log = LoggerFactory.getLogger(RefundService.class);

  private final RefundRepository refundRepository;
  private final VnPayClient vnPayClient;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  /** Nhận các phụ thuộc qua constructor. */
  public RefundService(
      RefundRepository refundRepository,
      VnPayClient vnPayClient,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.refundRepository = refundRepository;
    this.vnPayClient = vnPayClient;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

  /**
   * Khởi tạo hoàn tiền nếu đơn vừa hủy đã được thanh toán online. Gọi trong giao dịch hủy đơn; mỗi
   * đơn hoàn tiền tối đa 1 lần, nên hủy lại hay gọi lại không tạo thêm lần hoàn tiền nào.
   *
   * @param order đơn vừa chuyển Đã hủy
   */
  public void startIfPaid(Order order) {
    if (order.getPaymentStatus() != PaymentStatus.PAID || order.getRefund() != null) {
      return;
    }
    refundRepository.save(new Refund(order, LocalDateTime.now(clock)));
  }

  /**
   * Gọi VNPay cho các lần hoàn tiền đang chờ. Mỗi lần gọi nằm ngoài giao dịch CSDL (không giữ khóa
   * trong lúc chờ mạng), kết quả ghi lại trong giao dịch riêng.
   *
   * @return số lần hoàn tiền đã xử lý
   */
  public int processPending() {
    List<Long> ids =
        refundRepository.findTop20ByStatusOrderByIdAsc(RefundStatus.PROCESSING).stream()
            .map(Refund::getId)
            .toList();
    for (Long id : ids) {
      Optional<RefundRequest> request = transactionTemplate.execute(status -> requestFor(id));
      if (request.isEmpty()) {
        continue;
      }
      RefundResult result = vnPayClient.refund(request.get());
      transactionTemplate.executeWithoutResult(status -> record(id, result));
    }
    return ids.size();
  }

  /**
   * Quản trị viên xác nhận đã tự hoàn tiền cho đơn Hoàn tiền thất bại (US-17).
   *
   * @param orderId mã đơn
   * @throws NoSuchElementException nếu đơn không có lần hoàn tiền nào
   * @throws ConflictException nếu lần hoàn tiền không ở trạng thái thất bại
   */
  public void completeManually(long orderId) {
    Refund refund =
        refundRepository
            .findByOrderId(orderId)
            .orElseThrow(() -> new NoSuchElementException("Đơn này không có lần hoàn tiền nào"));
    if (refund.getStatus() != RefundStatus.FAILED) {
      throw new ConflictException("Chỉ xác nhận thủ công được lần hoàn tiền đã thất bại");
    }
    refund.completeManually(LocalDateTime.now(clock));
  }

  private Optional<RefundRequest> requestFor(long refundId) {
    Refund refund = refundRepository.findById(refundId).orElse(null);
    if (refund == null || refund.getStatus() != RefundStatus.PROCESSING) {
      return Optional.empty();
    }
    Order order = refund.getOrder();
    String txnRef = order.getPaymentTxnRef();
    return Optional.of(
        new RefundRequest(
            txnRef,
            order.getPaymentTransactionNo(),
            PaymentService.createDateOf(txnRef),
            refund.getAmount(),
            "Hoan tien don hang " + order.getId(),
            "127.0.0.1",
            ZonedDateTime.now(clock.withZone(VIETNAM))));
  }

  private void record(long refundId, RefundResult result) {
    Refund refund = refundRepository.findById(refundId).orElseThrow();
    if (refund.getStatus() != RefundStatus.PROCESSING) {
      return;
    }
    if (result.succeeded()) {
      refund.complete(LocalDateTime.now(clock));
      log.info("Đã hoàn tiền đơn {}", refund.getOrder().getId());
    } else {
      refund.recordFailure(result.failureReason());
      log.warn(
          "Hoàn tiền đơn {} thất bại lần {}: {}",
          refund.getOrder().getId(),
          refund.getRetryCount(),
          result.failureReason());
    }
  }
}
