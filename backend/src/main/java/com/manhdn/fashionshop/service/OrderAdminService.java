package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.AdminOrderResponse;
import com.manhdn.fashionshop.dto.AdminOrderSummaryResponse;
import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.entity.Order;
import com.manhdn.fashionshop.entity.OrderStatus;
import com.manhdn.fashionshop.entity.RecomputeTask;
import com.manhdn.fashionshop.entity.RefundStatus;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.OrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý đơn hàng của Quản trị viên (US-17): xem danh sách, lọc theo trạng thái, chuyển đơn sang
 * trạng thái kế tiếp hoặc hủy đơn thay khách.
 */
@Service
public class OrderAdminService {

  /** Độ dài tối đa của từ khóa tìm đơn. */
  public static final int MAX_KEYWORD_LENGTH = 100;

  private final OrderRepository orderRepository;
  private final AccountRepository accountRepository;
  private final OrderService orderService;
  private final RefundService refundService;
  private final RecomputeRequestService recomputeRequestService;
  private final TransactionRetry transactionRetry;
  private final Clock clock;

  /** Nhận các phụ thuộc qua constructor. */
  public OrderAdminService(
      OrderRepository orderRepository,
      AccountRepository accountRepository,
      OrderService orderService,
      RefundService refundService,
      RecomputeRequestService recomputeRequestService,
      TransactionRetry transactionRetry,
      Clock clock) {
    this.orderRepository = orderRepository;
    this.accountRepository = accountRepository;
    this.orderService = orderService;
    this.refundService = refundService;
    this.recomputeRequestService = recomputeRequestService;
    this.transactionRetry = transactionRetry;
    this.clock = clock;
  }

  /**
   * Danh sách đơn hàng, mới nhất trước.
   *
   * @param status chỉ lấy đơn ở trạng thái này, null là mọi trạng thái
   * @param keyword mã đơn, một phần email khách hoặc số điện thoại nhận; trống là không lọc
   * @param refundStatus chỉ lấy đơn có lần hoàn tiền ở trạng thái này, null là không lọc
   * @param page số trang, bắt đầu từ 0
   * @param size số đơn mỗi trang, từ 1 đến {@link OrderService#MAX_PAGE_SIZE}
   * @return một trang đơn hàng
   */
  @Transactional(readOnly = true)
  public PageResponse<AdminOrderSummaryResponse> list(
      OrderStatus status, String keyword, RefundStatus refundStatus, int page, int size) {
    if (page < 0 || size < 1 || size > OrderService.MAX_PAGE_SIZE) {
      throw new InvalidRequestException(
          "Trang bắt đầu từ 0, mỗi trang từ 1 đến %d đơn".formatted(OrderService.MAX_PAGE_SIZE));
    }
    String cleaned = keyword == null ? "" : keyword.strip();
    if (cleaned.length() > MAX_KEYWORD_LENGTH) {
      throw new InvalidRequestException("Từ khóa tối đa %d ký tự".formatted(MAX_KEYWORD_LENGTH));
    }
    return PageResponse.from(
        orderRepository.searchForAdmin(
            status, cleaned.isEmpty() ? null : cleaned, refundStatus, PageRequest.of(page, size)),
        AdminOrderSummaryResponse::from);
  }

  /**
   * Chi tiết một đơn hàng.
   *
   * @param orderId mã đơn hàng
   * @return chi tiết đơn kèm thông tin khách và trạng thái chuyển được
   * @throws NoSuchElementException nếu không có đơn
   */
  @Transactional(readOnly = true)
  public AdminOrderResponse get(long orderId) {
    return AdminOrderResponse.from(find(orderId));
  }

  /**
   * Đổi trạng thái đơn (BR-12): chỉ sang trạng thái kế tiếp (Chờ xử lý → Đang xử lý → Đang giao →
   * Đã giao), hoặc Đã hủy khi đơn chưa Đã giao (BR-13), có hoàn tồn kho (BR-16). Gửi lại đúng trạng
   * thái hiện tại thì không làm gì. Chuyển sang Đã giao hoặc Đã hủy thì yêu cầu tính lại gợi ý
   * (BR-20); Đã giao thì yêu cầu gán lại nhóm khách hàng (BR-23).
   *
   * @param orderId mã đơn hàng
   * @param target trạng thái mới
   * @return đơn sau khi đổi
   * @throws NoSuchElementException nếu không có đơn
   * @throws ConflictException nếu không chuyển được từ trạng thái hiện tại sang trạng thái này
   */
  public AdminOrderResponse changeStatus(long orderId, OrderStatus target) {
    return transactionRetry.execute(() -> change(orderId, target));
  }

  private AdminOrderResponse change(long orderId, OrderStatus target) {
    long accountId =
        orderRepository
            .findAccountIdById(orderId)
            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng"));
    accountRepository.findWithLockById(accountId);
    Order order = find(orderId);
    OrderStatus current = order.getStatus();
    if (current == target) {
      return AdminOrderResponse.from(order);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    if (target == OrderStatus.CANCELLED) {
      if (!current.adminCancellable()) {
        throw new ConflictException("Đơn đã giao xong, không hủy được nữa");
      }
      orderService.restoreStock(order);
      orderService.cancelWithPayment(order, now);
    } else if (target == current.next()) {
      if (!order.paymentSettledForProcessing()) {
        throw new ConflictException("Đơn thanh toán online chưa được thanh toán, chưa xử lý được");
      }
      order.advanceTo(target, now);
    } else {
      throw new ConflictException(
          current.next() == null
              ? "Đơn đã kết thúc, không đổi trạng thái được nữa"
              : "Chỉ chuyển được sang trạng thái kế tiếp hoặc hủy đơn");
    }
    if (target == OrderStatus.CANCELLED || target == OrderStatus.DELIVERED) {
      recomputeRequestService.request(
          accountId, RecomputeTask.RECOMMENDATION, RecomputeRequestService.STRONG_ACTION_DELAY);
    }
    if (target == OrderStatus.DELIVERED) {
      recomputeRequestService.request(
          accountId, RecomputeTask.SEGMENT_ASSIGN, RecomputeRequestService.STRONG_ACTION_DELAY);
    }
    return AdminOrderResponse.from(order);
  }

  /**
   * Xác nhận đã tự hoàn tiền cho đơn Hoàn tiền thất bại (BR-28, US-17), sau khi quản trị viên xử lý
   * qua cổng thanh toán hoặc liên hệ khách.
   *
   * @param orderId mã đơn hàng
   * @return đơn sau khi cập nhật
   * @throws NoSuchElementException nếu đơn không có lần hoàn tiền nào
   * @throws ConflictException nếu lần hoàn tiền không ở trạng thái thất bại
   */
  @Transactional
  public AdminOrderResponse completeRefundManually(long orderId) {
    refundService.completeManually(orderId);
    return AdminOrderResponse.from(find(orderId));
  }

  private Order find(long orderId) {
    return orderRepository
        .findById(orderId)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng"));
  }
}
