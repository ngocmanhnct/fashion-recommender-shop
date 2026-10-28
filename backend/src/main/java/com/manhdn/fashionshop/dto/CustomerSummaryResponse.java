package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.repository.AccountRepository.CustomerRow;
import java.time.LocalDateTime;

/**
 * Một khách hàng trong danh sách của trang quản trị (US-18).
 *
 * @param id mã tài khoản
 * @param fullName họ tên
 * @param email email đăng nhập
 * @param createdAt ngày đăng ký
 * @param orderCount số đơn đã đặt (mọi trạng thái)
 * @param segment nhóm hiện tại, null nếu chưa được phân nhóm (BR-24)
 */
public record CustomerSummaryResponse(
    long id,
    String fullName,
    String email,
    LocalDateTime createdAt,
    long orderCount,
    SegmentLabel segment) {

  /**
   * Mã và tên một nhóm khách hàng.
   *
   * @param code mã nhóm, vd VIP
   * @param name tên nhóm, vd Khách VIP
   */
  public record SegmentLabel(String code, String name) {}

  /** Chuyển dòng truy vấn sang DTO. */
  public static CustomerSummaryResponse from(CustomerRow row) {
    return new CustomerSummaryResponse(
        row.getId(),
        row.getFullName(),
        row.getEmail(),
        row.getCreatedAt(),
        row.getOrderCount(),
        row.getSegmentCode() == null
            ? null
            : new SegmentLabel(row.getSegmentCode(), row.getSegmentName()));
  }
}
