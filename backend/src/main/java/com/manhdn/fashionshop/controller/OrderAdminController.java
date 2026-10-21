package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.AdminOrderResponse;
import com.manhdn.fashionshop.dto.AdminOrderSummaryResponse;
import com.manhdn.fashionshop.dto.OrderStatusRequest;
import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.entity.OrderStatus;
import com.manhdn.fashionshop.service.OrderAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý đơn hàng (US-17), chỉ cho Quản trị viên (đường dẫn /api/admin, xem SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/orders")
public class OrderAdminController {

  private final OrderAdminService orderAdminService;

  /** Nhận OrderAdminService qua constructor. */
  public OrderAdminController(OrderAdminService orderAdminService) {
    this.orderAdminService = orderAdminService;
  }

  /**
   * Danh sách đơn hàng, mới nhất trước.
   *
   * @param status lọc theo trạng thái, bỏ trống là mọi trạng thái
   * @param keyword mã đơn, một phần email khách hoặc số điện thoại nhận
   * @param page số trang, bắt đầu từ 0
   * @param size số đơn mỗi trang, tối đa 50
   * @return 200 kèm một trang đơn hàng; 400 nếu tham số sai
   */
  @GetMapping
  public PageResponse<AdminOrderSummaryResponse> list(
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return orderAdminService.list(status, keyword, page, size);
  }

  /**
   * Chi tiết một đơn hàng.
   *
   * @param id mã đơn hàng
   * @return 200 kèm chi tiết đơn; 404 nếu không có
   */
  @GetMapping("/{id}")
  public AdminOrderResponse get(@PathVariable long id) {
    return orderAdminService.get(id);
  }

  /**
   * Đổi trạng thái đơn: sang trạng thái kế tiếp, hoặc CANCELLED khi đơn chưa Đã giao.
   *
   * @param id mã đơn hàng
   * @param request trạng thái mới
   * @return 200 kèm đơn sau khi đổi; 404 nếu không có đơn; 409 nếu không chuyển được
   */
  @PutMapping("/{id}/status")
  public AdminOrderResponse changeStatus(
      @PathVariable long id, @Valid @RequestBody OrderStatusRequest request) {
    return orderAdminService.changeStatus(id, request.status());
  }
}
