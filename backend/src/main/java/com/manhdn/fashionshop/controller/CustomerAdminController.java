package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.CustomerDetailResponse;
import com.manhdn.fashionshop.dto.CustomerSummaryResponse;
import com.manhdn.fashionshop.dto.OrderSummaryResponse;
import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.SegmentOverviewResponse;
import com.manhdn.fashionshop.dto.SegmentProfileResponse;
import com.manhdn.fashionshop.service.CustomerAdminService;
import com.manhdn.fashionshop.service.SegmentProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API xem khách hàng (US-18) và kết quả phân nhóm khách hàng (US-19), chỉ cho Quản trị viên (đường
 * dẫn /api/admin, xem SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin")
public class CustomerAdminController {

  private final CustomerAdminService customerAdminService;
  private final SegmentProfileService segmentProfileService;

  /** Nhận CustomerAdminService, SegmentProfileService qua constructor. */
  public CustomerAdminController(
      CustomerAdminService customerAdminService, SegmentProfileService segmentProfileService) {
    this.customerAdminService = customerAdminService;
    this.segmentProfileService = segmentProfileService;
  }

  /**
   * Danh sách khách hàng, mới đăng ký trước.
   *
   * @param keyword một phần họ tên hoặc email
   * @param segment mã nhóm (vd VIP), NONE là khách chưa được phân nhóm; bỏ trống là mọi khách
   * @param page số trang, bắt đầu từ 0
   * @param size số khách mỗi trang, tối đa 50
   * @return 200 kèm một trang khách hàng; 400 nếu tham số sai
   */
  @GetMapping("/customers")
  public PageResponse<CustomerSummaryResponse> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String segment,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return customerAdminService.list(keyword, segment, page, size);
  }

  /**
   * Chi tiết một khách hàng: thông tin, thống kê đơn và nhóm hiện tại.
   *
   * @param id mã tài khoản
   * @return 200 kèm chi tiết; 404 nếu không có khách hàng này
   */
  @GetMapping("/customers/{id}")
  public CustomerDetailResponse get(@PathVariable long id) {
    return customerAdminService.get(id);
  }

  /**
   * Lịch sử đơn hàng của một khách, mới nhất trước.
   *
   * @param id mã tài khoản
   * @param page số trang, bắt đầu từ 0
   * @param size số đơn mỗi trang, tối đa 50
   * @return 200 kèm một trang đơn hàng; 400 nếu tham số sai; 404 nếu không có khách hàng này
   */
  @GetMapping("/customers/{id}/orders")
  public PageResponse<OrderSummaryResponse> orders(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return customerAdminService.orders(id, page, size);
  }

  /**
   * Kết quả phân nhóm khách hàng: lần huấn luyện gần nhất, các nhóm kèm số khách và R, F, M trung
   * bình, số khách chưa được phân nhóm.
   *
   * @return 200 kèm tổng quan phân nhóm
   */
  @GetMapping("/segments")
  public SegmentOverviewResponse segments() {
    return customerAdminService.segments();
  }

  /**
   * Hồ sơ một nhóm khách hàng (US-25): doanh thu, giá trị mỗi đơn, nhịp mua, thời gian gắn bó, danh
   * mục mua nhiều, giới tính, độ tuổi và khách cần chăm sóc (BR-43).
   *
   * @param code mã nhóm, vd VIP
   * @return 200 kèm hồ sơ nhóm, 404 nếu không có nhóm này
   */
  @GetMapping("/segments/{code}")
  public SegmentProfileResponse segmentProfile(@PathVariable String code) {
    return segmentProfileService.profile(code);
  }
}
