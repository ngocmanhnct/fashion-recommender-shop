package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.MyReviewResponse;
import com.manhdn.fashionshop.dto.ProductReviewsResponse;
import com.manhdn.fashionshop.dto.ReviewRequest;
import com.manhdn.fashionshop.dto.ReviewResponse;
import com.manhdn.fashionshop.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API đánh giá sản phẩm (US-15). Xem đánh giá công khai; xem và gửi đánh giá của mình chỉ cho Khách
 * hàng (không token 401, Quản trị viên 403).
 */
@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {

  private final ReviewService reviewService;

  /** Nhận service qua constructor. */
  public ReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  /**
   * Đánh giá của sản phẩm: điểm trung bình, số đánh giá theo mức sao và một trang đánh giá.
   *
   * @param productId mã sản phẩm
   * @param page số trang, từ 0 (mặc định 0)
   * @param size số đánh giá mỗi trang, từ 1 đến 20 (mặc định 5)
   * @return 200 kèm phần đánh giá; 400 nếu số trang sai; 404 nếu sản phẩm không có hoặc ngừng bán
   */
  @GetMapping
  public ProductReviewsResponse reviews(
      @PathVariable Long productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "5") int size) {
    return reviewService.reviews(productId, page, size);
  }

  /**
   * Khách hàng đã được đánh giá sản phẩm chưa (đã nhận hàng, BR-17) và đánh giá đã gửi.
   *
   * @param productId mã sản phẩm
   * @param jwt token của Khách hàng, claim sub là mã tài khoản
   * @return 200 kèm canReview và review (null nếu chưa đánh giá); 404 nếu sản phẩm không có
   */
  @GetMapping("/me")
  public MyReviewResponse myReview(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt) {
    return reviewService.myReview(accountId(jwt), productId);
  }

  /**
   * Gửi đánh giá, hoặc sửa đánh giá đã gửi (BR-18: mỗi sản phẩm tối đa 1 đánh giá).
   *
   * @param productId mã sản phẩm
   * @param request số sao (1 đến 5) và nhận xét (tối đa 1.000 ký tự, không bắt buộc)
   * @param jwt token của Khách hàng
   * @return 200 kèm đánh giá đã lưu; 400 nếu dữ liệu sai; 404 nếu sản phẩm không có hoặc ngừng bán;
   *     409 nếu chưa có đơn Đã giao chứa sản phẩm này (BR-17)
   */
  @PutMapping("/me")
  public ReviewResponse save(
      @PathVariable Long productId,
      @Valid @RequestBody ReviewRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return reviewService.save(accountId(jwt), productId, request);
  }

  private static long accountId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
