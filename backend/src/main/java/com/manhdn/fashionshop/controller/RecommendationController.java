package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.RecommendationEventRequest;
import com.manhdn.fashionshop.dto.RecommendationsResponse;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.security.JwtService;
import com.manhdn.fashionshop.service.RecommendationEventService;
import com.manhdn.fashionshop.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API gợi ý: danh sách ở trang chủ (US-13, chỉ khách hàng) và nhật ký lượt hiện, lượt bấm gợi ý
 * (công khai, để tính tỷ lệ bấm).
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

  private final RecommendationService recommendationService;
  private final RecommendationEventService eventService;

  /** Nhận service qua constructor. */
  public RecommendationController(
      RecommendationService recommendationService, RecommendationEventService eventService) {
    this.recommendationService = recommendationService;
    this.eventService = eventService;
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

  /**
   * Ghi lượt hiện (cả khối gợi ý lọt vào màn hình) hoặc lượt bấm một sản phẩm gợi ý. Công khai:
   * khách chưa đăng nhập ghi với tài khoản trống; Quản trị viên không được ghi (bỏ qua, vẫn 204).
   *
   * @param jwt token nếu đã đăng nhập, null nếu chưa
   * @param request loại sự kiện, vị trí và các sản phẩm
   */
  @PostMapping("/events")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void recordEvent(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RecommendationEventRequest request) {
    if (jwt != null && Role.ADMIN.name().equals(jwt.getClaimAsString(JwtService.ROLE_CLAIM))) {
      return;
    }
    eventService.record(jwt == null ? null : Long.parseLong(jwt.getSubject()), request);
  }
}
