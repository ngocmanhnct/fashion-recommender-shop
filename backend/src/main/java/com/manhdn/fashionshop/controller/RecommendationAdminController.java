package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.RecommendationCtrResponse;
import com.manhdn.fashionshop.service.RecommendationEventService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API thống kê gợi ý cho Quản trị viên (chỉ ADMIN, khai trong SecurityConfig). */
@RestController
@RequestMapping("/api/admin/recommendations")
public class RecommendationAdminController {

  private final RecommendationEventService eventService;

  /** Nhận service qua constructor. */
  public RecommendationAdminController(RecommendationEventService eventService) {
    this.eventService = eventService;
  }

  /**
   * Tỷ lệ bấm vào gợi ý (CTR = lượt bấm ÷ lượt hiện × 100%) trong khoảng ngày, tổng và theo vị trí,
   * nguồn. Bỏ trống thì lấy 30 ngày gần nhất.
   *
   * @param from ngày đầu (yyyy-MM-dd), không bắt buộc
   * @param to ngày cuối (yyyy-MM-dd), không bắt buộc
   * @return số lượt hiện, lượt bấm và tỷ lệ bấm
   */
  @GetMapping("/ctr")
  public RecommendationCtrResponse ctr(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return eventService.ctr(from, to);
  }
}
