package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Gender;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Chi tiết một khách hàng cho trang quản trị (US-18), kèm nhóm hiện tại (US-19).
 *
 * @param id mã tài khoản
 * @param fullName họ tên
 * @param email email đăng nhập
 * @param gender giới tính, null nếu chưa khai
 * @param birthdate ngày sinh, null nếu chưa khai
 * @param createdAt ngày đăng ký
 * @param orderCount số đơn đã đặt (mọi trạng thái)
 * @param deliveredOrderCount số đơn Đã giao
 * @param deliveredTotal tổng tiền các đơn Đã giao (VNĐ)
 * @param segment nhóm hiện tại, null nếu chưa được phân nhóm (BR-24)
 */
public record CustomerDetailResponse(
    long id,
    String fullName,
    String email,
    Gender gender,
    LocalDate birthdate,
    LocalDateTime createdAt,
    long orderCount,
    long deliveredOrderCount,
    long deliveredTotal,
    SegmentInfo segment) {

  /**
   * Nhóm của khách và R, F, M lúc xếp nhóm.
   *
   * @param code mã nhóm
   * @param name tên nhóm
   * @param description ý nghĩa, số liệu trung bình và gợi ý chăm sóc của nhóm
   * @param recency R: số ngày từ đơn Đã giao gần nhất, tính lúc xếp nhóm
   * @param frequency F: số đơn Đã giao lúc xếp nhóm
   * @param monetary M: tổng tiền các đơn Đã giao lúc xếp nhóm (VNĐ)
   * @param computedAt thời điểm xếp nhóm
   */
  public record SegmentInfo(
      String code,
      String name,
      String description,
      int recency,
      int frequency,
      long monetary,
      LocalDateTime computedAt) {}
}
