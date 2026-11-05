package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.PendingReviewsResponse;
import com.manhdn.fashionshop.service.ReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sản phẩm chờ đánh giá của Khách hàng (US-15): để đánh giá ngay ở trang đơn hàng, không phải tìm
 * lại từng sản phẩm. Chỉ cho Khách hàng (không token 401, Quản trị viên 403).
 */
@RestController
public class PendingReviewController {

  private final ReviewService reviewService;

  /** Nhận service qua constructor. */
  public PendingReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  /**
   * Các sản phẩm thuộc đơn Đã giao mà khách chưa đánh giá, đơn mới nhất trước.
   *
   * @param jwt token của Khách hàng, claim sub là mã tài khoản
   * @return 200 kèm tổng số và tối đa 20 sản phẩm
   */
  @GetMapping("/api/reviews/pending")
  public PendingReviewsResponse pending(@AuthenticationPrincipal Jwt jwt) {
    return reviewService.pending(Long.parseLong(jwt.getSubject()));
  }
}
