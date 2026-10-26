package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.RecommendationsResponse;
import com.manhdn.fashionshop.service.RecommendationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API gợi ý cá nhân hóa ở trang chủ (US-13), chỉ cho khách hàng đã đăng nhập. */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

  private final RecommendationService recommendationService;

  /** Nhận service qua constructor. */
  public RecommendationController(RecommendationService recommendationService) {
    this.recommendationService = recommendationService;
  }

  /**
   * Khối "Gợi ý cho bạn" của khách hàng đang đăng nhập: tối đa 8 sản phẩm kèm lý do và nguồn của
   * danh sách (BEHAVIOR, SURVEY hoặc POPULAR).
   *
   * @param jwt token của khách hàng
   * @return nguồn và danh sách sản phẩm
   */
  @GetMapping("/me")
  public RecommendationsResponse mine(@AuthenticationPrincipal Jwt jwt) {
    return recommendationService.forAccount(Long.parseLong(jwt.getSubject()));
  }
}
