package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.client.VnPayClient;
import com.manhdn.fashionshop.client.VnPayClient.PaymentRequest;
import com.manhdn.fashionshop.config.VnPayProperties;
import com.manhdn.fashionshop.dto.PaymentUrlResponse;
import com.manhdn.fashionshop.entity.Order;
import com.manhdn.fashionshop.entity.OrderStatus;
import com.manhdn.fashionshop.entity.PaymentStatus;
import com.manhdn.fashionshop.entity.RecomputeTask;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.OrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Thanh toán online qua VNPay (US-10): tạo đường dẫn thanh toán, xử lý kết quả VNPay gửi về (trang
 * trả về và IPN, cùng một cách, gửi lại nhiều lần không sao), tự hủy đơn quá hạn thanh toán
 * (BR-34).
 */
@Service
public class PaymentService {

  /** Độ dài phần thời điểm tạo (yyyyMMddHHmmss) ở cuối mã tham chiếu vnp_TxnRef. */
  private static final int CREATE_DATE_LENGTH = 14;

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final OrderRepository orderRepository;
  private final AccountRepository accountRepository;
  private final OrderService orderService;
  private final RefundService refundService;
  private final RecomputeRequestService recomputeRequestService;
  private final VnPayClient vnPayClient;
  private final VnPayProperties properties;
  private final TransactionRetry transactionRetry;
  private final Clock clock;

  /** Nhận các phụ thuộc qua constructor. */
  public PaymentService(
      OrderRepository orderRepository,
      AccountRepository accountRepository,
      OrderService orderService,
      RefundService refundService,
      RecomputeRequestService recomputeRequestService,
      VnPayClient vnPayClient,
      VnPayProperties properties,
      TransactionRetry transactionRetry,
      Clock clock) {
    this.orderRepository = orderRepository;
    this.accountRepository = accountRepository;
    this.orderService = orderService;
    this.refundService = refundService;
    this.recomputeRequestService = recomputeRequestService;
    this.vnPayClient = vnPayClient;
    this.properties = properties;
    this.transactionRetry = transactionRetry;
    this.clock = clock;
  }

  /**
   * Thời điểm tạo giao dịch (yyyyMMddHHmmss, giờ Việt Nam) nằm ở cuối mã tham chiếu.
   *
   * @param txnRef mã tham chiếu dạng mã đơn + thời điểm tạo
   * @return thời điểm tạo
   */
  public static String createDateOf(String txnRef) {
    return txnRef.substring(txnRef.length() - CREATE_DATE_LENGTH);
  }

  /**
   * Tạo đường dẫn thanh toán VNPay cho đơn ONLINE còn chờ thanh toán (lần đầu hoặc thanh toán lại
   * sau khi bỏ ngang). Đường dẫn hết hiệu lực trước hạn thanh toán của đơn (BR-34).
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param orderId mã đơn hàng
   * @param ipAddress địa chỉ IP của khách
   * @return đường dẫn thanh toán và thời điểm hết hiệu lực
   * @throws NoSuchElementException nếu đơn không có hoặc thuộc tài khoản khác
   * @throws ConflictException nếu đơn không còn chờ thanh toán hoặc đã quá hạn
   * @throws InvalidRequestException nếu cửa hàng chưa cấu hình VNPay
   */
  public PaymentUrlResponse createPaymentUrl(long accountId, long orderId, String ipAddress) {
    if (!vnPayClient.configured()) {
      throw new InvalidRequestException("Cửa hàng chưa bật thanh toán online qua VNPay");
    }
    Order order =
        orderRepository
            .findByIdAndAccountId(orderId, accountId)
            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng"));
    if (!order.awaitingPayment()) {
      throw new ConflictException("Đơn hàng không còn chờ thanh toán");
    }
    ZonedDateTime now = ZonedDateTime.now(clock.withZone(RefundService.VIETNAM));
    ZonedDateTime deadline =
        order.paymentDeadline().atZone(clock.getZone()).withZoneSameInstant(RefundService.VIETNAM);
    if (!now.isBefore(deadline)) {
      throw new ConflictException("Đơn hàng đã quá hạn thanh toán và sẽ tự hủy");
    }
    ZonedDateTime expiresAt = now.plus(properties.linkTtl());
    if (expiresAt.isAfter(deadline)) {
      expiresAt = deadline;
    }
    String txnRef = order.getId() + VnPayClient.VNPAY_TIME.format(now);
    String url =
        vnPayClient.paymentUrl(
            new PaymentRequest(
                txnRef,
                order.getTotalAmount(),
                "Thanh toan don hang " + order.getId(),
                ipAddress,
                now,
                expiresAt));
    return new PaymentUrlResponse(url, expiresAt.toLocalDateTime());
  }

  /**
   * Xử lý kết quả thanh toán VNPay gửi về. Trang trả về (trình duyệt) và IPN (máy chủ VNPay gọi)
   * dùng chung hàm này; gọi lại nhiều lần cho cùng giao dịch chỉ ghi nhận 1 lần. Thanh toán thành
   * công cho đơn đã bị hủy (vd quá hạn rồi khách mới trả tiền) thì ghi nhận và hoàn tiền (BR-28).
   *
   * @param params toàn bộ tham số VNPay gửi kèm
   * @return kết quả xử lý
   */
  public CallbackResult handleCallback(Map<String, String> params) {
    if (!vnPayClient.verify(params)) {
      return new CallbackResult(CallbackOutcome.INVALID_SIGNATURE, null);
    }
    Optional<Long> orderId = orderIdOf(params.get("vnp_TxnRef"));
    if (orderId.isEmpty()) {
      return new CallbackResult(CallbackOutcome.ORDER_NOT_FOUND, null);
    }
    return transactionRetry.execute(() -> apply(orderId.get(), params));
  }

  /**
   * Hủy các đơn ONLINE quá {@link Order#PAYMENT_TIMEOUT} chưa thanh toán (BR-34): thanh toán chuyển
   * FAILED, đơn chuyển Đã hủy, hoàn tồn kho; không hoàn tiền vì chưa có tiền nào được trả.
   *
   * @return số đơn đã hủy
   */
  public int cancelExpiredOnlineOrders() {
    LocalDateTime cutoff = LocalDateTime.now(clock).minus(Order.PAYMENT_TIMEOUT);
    List<Long> ids = orderRepository.findIdsAwaitingPaymentBefore(cutoff);
    int cancelled = 0;
    for (Long id : ids) {
      if (Boolean.TRUE.equals(transactionRetry.execute(() -> cancelExpired(id, cutoff)))) {
        cancelled++;
      }
    }
    if (cancelled > 0) {
      log.info("Đã hủy {} đơn ONLINE quá hạn thanh toán", cancelled);
    }
    return cancelled;
  }

  private Boolean cancelExpired(long orderId, LocalDateTime cutoff) {
    long accountId = orderRepository.findAccountIdById(orderId).orElseThrow();
    accountRepository.findWithLockById(accountId);
    Order order = orderRepository.findById(orderId).orElseThrow();
    if (!order.awaitingPayment() || order.getCreatedAt().isAfter(cutoff)) {
      return false;
    }
    orderService.restoreStock(order);
    orderService.cancelWithPayment(order, LocalDateTime.now(clock));
    recomputeRequestService.request(
        accountId, RecomputeTask.RECOMMENDATION, RecomputeRequestService.STRONG_ACTION_DELAY);
    return true;
  }

  private CallbackResult apply(long orderId, Map<String, String> params) {
    Optional<Long> accountId = orderRepository.findAccountIdById(orderId);
    if (accountId.isEmpty()) {
      return new CallbackResult(CallbackOutcome.ORDER_NOT_FOUND, null);
    }
    accountRepository.findWithLockById(accountId.get());
    Order order = orderRepository.findById(orderId).orElseThrow();
    if (!String.valueOf(order.getTotalAmount() * 100).equals(params.get("vnp_Amount"))) {
      return new CallbackResult(CallbackOutcome.INVALID_AMOUNT, orderId);
    }
    if (order.getPaymentStatus() == PaymentStatus.PAID) {
      return new CallbackResult(CallbackOutcome.ALREADY_CONFIRMED, orderId);
    }
    boolean paid =
        "00".equals(params.get("vnp_ResponseCode"))
            && "00".equals(params.get("vnp_TransactionStatus"));
    if (!paid) {
      // Khách hủy hoặc thanh toán lỗi: đơn vẫn chờ thanh toán đến hạn (BR-34), khách thử lại được.
      return new CallbackResult(CallbackOutcome.PAYMENT_FAILED, orderId);
    }
    order.markPaid(
        params.get("vnp_TxnRef"), params.get("vnp_TransactionNo"), LocalDateTime.now(clock));
    if (order.getStatus() == OrderStatus.CANCELLED) {
      log.warn("Đơn {} đã hủy nhưng VNPay báo thanh toán thành công, khởi tạo hoàn tiền", orderId);
      refundService.startIfPaid(order);
      return new CallbackResult(CallbackOutcome.PAID_AFTER_CANCEL, orderId);
    }
    return new CallbackResult(CallbackOutcome.PAID, orderId);
  }

  /** Mã đơn ở đầu mã tham chiếu (mã đơn + 14 chữ số thời điểm tạo). */
  private static Optional<Long> orderIdOf(String txnRef) {
    if (txnRef == null
        || txnRef.length() <= CREATE_DATE_LENGTH
        || txnRef.length() > CREATE_DATE_LENGTH + 18
        || !txnRef.chars().allMatch(Character::isDigit)) {
      return Optional.empty();
    }
    return Optional.of(Long.parseLong(txnRef.substring(0, txnRef.length() - CREATE_DATE_LENGTH)));
  }

  /** Kết quả xử lý dữ liệu VNPay gửi về. */
  public enum CallbackOutcome {
    /** Thanh toán thành công, đơn chuyển Đã thanh toán. */
    PAID,
    /** Đã ghi nhận thanh toán từ trước (VNPay gửi lại). */
    ALREADY_CONFIRMED,
    /** Thanh toán thành công nhưng đơn đã bị hủy, đã khởi tạo hoàn tiền (BR-28). */
    PAID_AFTER_CANCEL,
    /** Khách hủy hoặc thanh toán không thành công. */
    PAYMENT_FAILED,
    /** Sai chữ ký: không phải dữ liệu của VNPay. */
    INVALID_SIGNATURE,
    /** Không có đơn ứng với mã tham chiếu. */
    ORDER_NOT_FOUND,
    /** Số tiền không khớp tổng thanh toán của đơn. */
    INVALID_AMOUNT
  }

  /**
   * Kết quả xử lý.
   *
   * @param outcome loại kết quả
   * @param orderId mã đơn, null nếu không xác định được
   */
  public record CallbackResult(CallbackOutcome outcome, Long orderId) {}
}
