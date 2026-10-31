package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.MyReviewResponse;
import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.ProductReviewsResponse;
import com.manhdn.fashionshop.dto.ReviewRequest;
import com.manhdn.fashionshop.dto.ReviewResponse;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.OrderItem;
import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.Review;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.ProductRepository;
import com.manhdn.fashionshop.repository.ReviewRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đánh giá sản phẩm (US-15, BR-17, BR-18).
 *
 * <ul>
 *   <li>Ai cũng xem được đánh giá của sản phẩm đang bán: điểm trung bình, số đánh giá theo mức sao
 *       và danh sách theo trang, sửa gần nhất trước. Người đánh giá chỉ hiện tên gọi.
 *   <li>Khách hàng chỉ đánh giá được sản phẩm thuộc một đơn Đã giao của mình (BR-17); mỗi sản phẩm
 *       tối đa 1 đánh giá, gửi lại thì sửa đánh giá cũ (BR-18). Đánh giá gắn với dòng đơn Đã giao
 *       mới nhất có sản phẩm đó.
 *   <li>Điểm đánh giá vào mô hình gợi ý qua điểm phổ biến; ml-service tự dựng lại mô hình khi bảng
 *       review đổi nên không cần yêu cầu tính lại.
 * </ul>
 */
@Service
public class ReviewService {

  /** Số đánh giá tối đa mỗi trang. */
  public static final int MAX_PAGE_SIZE = 20;

  private static final int STARS = 5;
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

  private final TransactionRetry transactionRetry;
  private final AccountRepository accountRepository;
  private final ProductRepository productRepository;
  private final ReviewRepository reviewRepository;
  private final Clock clock;

  /** Nhận các phụ thuộc qua constructor. */
  public ReviewService(
      TransactionRetry transactionRetry,
      AccountRepository accountRepository,
      ProductRepository productRepository,
      ReviewRepository reviewRepository,
      Clock clock) {
    this.transactionRetry = transactionRetry;
    this.accountRepository = accountRepository;
    this.productRepository = productRepository;
    this.reviewRepository = reviewRepository;
    this.clock = clock;
  }

  /**
   * Phần đánh giá của trang sản phẩm.
   *
   * @param productId mã sản phẩm
   * @param page số trang, từ 0
   * @param size số đánh giá mỗi trang, từ 1 đến {@link #MAX_PAGE_SIZE}
   * @return điểm trung bình, số đánh giá theo mức sao và một trang đánh giá
   * @throws NoSuchElementException nếu sản phẩm không có hoặc đã ngừng bán
   * @throws InvalidRequestException nếu số trang hoặc cỡ trang sai
   */
  @Transactional(readOnly = true)
  public ProductReviewsResponse reviews(long productId, int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidRequestException(
          "Số trang từ 0, mỗi trang từ 1 đến " + MAX_PAGE_SIZE + " đánh giá");
    }
    activeProduct(productId);
    List<Long> starCounts = new ArrayList<>(Collections.nCopies(STARS, 0L));
    for (Object[] row : reviewRepository.countByRating(productId)) {
      starCounts.set(((Number) row[0]).intValue() - 1, ((Number) row[1]).longValue());
    }
    long count = 0;
    long sum = 0;
    for (int star = 1; star <= STARS; star++) {
      count += starCounts.get(star - 1);
      sum += star * starCounts.get(star - 1);
    }
    Double average = count == 0 ? null : Math.round(sum * 10.0 / count) / 10.0;
    PageResponse<ReviewResponse> reviews =
        PageResponse.from(
            reviewRepository.findByProductId(productId, PageRequest.of(page, size, NEWEST_FIRST)),
            ReviewResponse::from);
    return new ProductReviewsResponse(average, count, starCounts, reviews);
  }

  /**
   * Khách hàng đã được đánh giá sản phẩm chưa và đánh giá đã gửi (nếu có).
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param productId mã sản phẩm
   * @return trạng thái đánh giá
   * @throws NoSuchElementException nếu sản phẩm không có hoặc đã ngừng bán
   */
  @Transactional(readOnly = true)
  public MyReviewResponse myReview(long accountId, long productId) {
    activeProduct(productId);
    boolean canReview = !reviewRepository.findDeliveredItems(accountId, productId).isEmpty();
    ReviewResponse review =
        reviewRepository
            .findByAccountIdAndProductId(accountId, productId)
            .map(ReviewResponse::from)
            .orElse(null);
    return new MyReviewResponse(canReview, review);
  }

  /**
   * Gửi hoặc sửa đánh giá của khách hàng cho một sản phẩm đã nhận hàng.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param productId mã sản phẩm
   * @param request số sao và nhận xét
   * @return đánh giá sau khi lưu
   * @throws NoSuchElementException nếu tài khoản không có, hoặc sản phẩm không có hay đã ngừng bán
   * @throws ConflictException nếu khách chưa có đơn Đã giao chứa sản phẩm này (BR-17)
   */
  public ReviewResponse save(long accountId, long productId, ReviewRequest request) {
    String comment =
        request.comment() == null ? null : TextNormalizer.nfc(request.comment().strip());
    String saved = comment == null || comment.isEmpty() ? null : comment;
    return transactionRetry.execute(
        () -> {
          Account account =
              accountRepository
                  .findWithLockById(accountId)
                  .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản"));
          Product product = activeProduct(productId);
          List<OrderItem> delivered = reviewRepository.findDeliveredItems(accountId, productId);
          if (delivered.isEmpty()) {
            throw new ConflictException(
                "Chỉ đánh giá được sản phẩm thuộc đơn hàng đã giao cho bạn");
          }
          OrderItem latest = delivered.get(0);
          LocalDateTime now = LocalDateTime.now(clock);
          Review review =
              reviewRepository
                  .findByAccountIdAndProductId(accountId, productId)
                  .map(
                      existing -> {
                        existing.edit(latest, request.rating(), saved, now);
                        return existing;
                      })
                  .orElseGet(
                      () ->
                          reviewRepository.save(
                              new Review(account, product, latest, request.rating(), saved, now)));
          return ReviewResponse.from(review);
        });
  }

  private Product activeProduct(long productId) {
    return productRepository
        .findById(productId)
        .filter(p -> !p.isDeleted())
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy sản phẩm"));
  }
}
