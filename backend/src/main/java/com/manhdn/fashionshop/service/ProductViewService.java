package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.ProductViewLog;
import com.manhdn.fashionshop.entity.RecomputeTask;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.ProductRepository;
import com.manhdn.fashionshop.repository.ProductViewLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * Ghi lượt xem sản phẩm của tài khoản đã đăng nhập (US-03) và yêu cầu tính lại gợi ý (BR-20).
 *
 * <p>Ngưỡng BR-19 về lượt xem: tài khoản đã xem ít nhất {@link #VIEW_THRESHOLD} sản phẩm khác nhau
 * trong {@link #VIEW_WINDOW_DAYS} ngày gần nhất (viewed_at tính theo giờ của JVM backend như các
 * bảng khác, sản phẩm đã xóa mềm vẫn được đếm). Lượt xem làm tài khoản vừa đạt ngưỡng (sản phẩm
 * khác nhau thứ 5, khi tài khoản chưa có đơn đã hoàn tất nào) được xử lý như hành động rõ ý: gợi ý
 * được tính lại sau vài giây thay vì tối đa 30 giây.
 */
@Service
public class ProductViewService {

  /** Số sản phẩm khác nhau đã xem để đạt ngưỡng "đủ dữ liệu hành vi" (BR-19). */
  public static final int VIEW_THRESHOLD = 5;

  /** Khoảng thời gian tính ngưỡng lượt xem, tính bằng ngày (BR-19). */
  public static final int VIEW_WINDOW_DAYS = 30;

  private final TransactionRetry transactionRetry;
  private final AccountRepository accountRepository;
  private final ProductRepository productRepository;
  private final ProductViewLogRepository viewLogRepository;
  private final RecomputeRequestService recomputeRequestService;
  private final Clock clock;

  /** Nhận các thành phần cần thiết qua constructor. */
  public ProductViewService(
      TransactionRetry transactionRetry,
      AccountRepository accountRepository,
      ProductRepository productRepository,
      ProductViewLogRepository viewLogRepository,
      RecomputeRequestService recomputeRequestService,
      Clock clock) {
    this.transactionRetry = transactionRetry;
    this.accountRepository = accountRepository;
    this.productRepository = productRepository;
    this.viewLogRepository = viewLogRepository;
    this.recomputeRequestService = recomputeRequestService;
    this.clock = clock;
  }

  /**
   * Ghi một lượt xem (tài khoản, sản phẩm, thời điểm) và yêu cầu tính lại gợi ý của tài khoản.
   *
   * <p>Chạy trong giao dịch đọc-ghi riêng, tự thử lại khi MySQL báo khóa chéo. Dòng tài khoản được
   * khóa đầu tiên, nên các lượt xem đồng thời của cùng một tài khoản chạy lần lượt và không bỏ lỡ
   * lượt vừa đạt ngưỡng; thứ tự khóa luôn là account, product_view_log, recompute_request.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param productId mã sản phẩm đang xem
   * @throws NoSuchElementException nếu tài khoản không còn tồn tại, hoặc sản phẩm không có hay đã
   *     ngừng bán (khi đó không ghi gì)
   */
  public void recordView(long accountId, long productId) {
    transactionRetry.run(() -> record(accountId, productId));
  }

  private void record(long accountId, long productId) {
    Account account =
        accountRepository
            .findWithLockById(accountId)
            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản"));
    if (!productRepository.existsByIdAndDeletedAtIsNull(productId)) {
      throw new NoSuchElementException("Không tìm thấy sản phẩm");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime since = now.minusDays(VIEW_WINDOW_DAYS);
    boolean alreadyViewed =
        viewLogRepository.existsByAccountIdAndProductIdAndViewedAtGreaterThanEqual(
            accountId, productId, since);
    long distinctBefore = viewLogRepository.countDistinctProductsSince(accountId, since);
    viewLogRepository.saveAndFlush(
        new ProductViewLog(account, productRepository.getReferenceById(productId), now));
    Duration delay =
        justReachesThreshold(accountId, alreadyViewed, distinctBefore)
            ? RecomputeRequestService.STRONG_ACTION_DELAY
            : RecomputeRequestService.VIEW_DELAY;
    recomputeRequestService.request(accountId, RecomputeTask.RECOMMENDATION, delay);
  }

  /**
   * Lượt xem này làm tài khoản vừa đạt ngưỡng BR-19: là sản phẩm mới (chưa xem trong khoảng thời
   * gian tính), trước đó đã xem đúng 4 sản phẩm khác nhau, và tài khoản chưa có đơn đã hoàn tất (có
   * rồi thì tài khoản đã đạt ngưỡng từ trước).
   */
  private boolean justReachesThreshold(long accountId, boolean alreadyViewed, long distinctBefore) {
    return !alreadyViewed
        && distinctBefore == VIEW_THRESHOLD - 1
        && viewLogRepository.countDeliveredOrders(accountId) == 0;
  }
}
