package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.DashboardResponse;
import com.manhdn.fashionshop.service.DashboardService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API trang tổng quan cho Quản trị viên (chỉ ADMIN, khai trong SecurityConfig). */
@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardAdminController {

  private final DashboardService dashboardService;

  /** Nhận service qua constructor. */
  public DashboardAdminController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  /**
   * Doanh thu theo ngày, số đơn theo trạng thái, 10 sản phẩm bán chạy và hiệu quả gợi ý trong
   * khoảng ngày (giờ Việt Nam). Bỏ trống thì lấy 30 ngày gần nhất; tối đa 366 ngày.
   *
   * @param from ngày đầu (yyyy-MM-dd), không bắt buộc
   * @param to ngày cuối (yyyy-MM-dd), không bắt buộc
   * @return số liệu tổng quan; 400 nếu ngày đầu sau ngày cuối hoặc khoảng quá dài
   */
  @GetMapping
  public DashboardResponse overview(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return dashboardService.overview(from, to);
  }
}
