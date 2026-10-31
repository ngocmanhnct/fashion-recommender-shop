package com.manhdn.fashionshop.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hồ sơ một nhóm khách hàng cho Quản trị viên (US-25): quy mô, doanh thu, thói quen mua, danh mục
 * ưa thích, giới tính, độ tuổi và danh sách khách cần chăm sóc (BR-43). Chỉ tính đơn Đã giao.
 *
 * @param code mã nhóm
 * @param name tên nhóm
 * @param description ý nghĩa và gợi ý chăm sóc của nhóm
 * @param customerCount số khách hiện thuộc nhóm
 * @param customerShare tỷ lệ trên số khách đã phân nhóm (%)
 * @param revenue tổng tiền các đơn Đã giao của khách trong nhóm (VNĐ)
 * @param revenueShare tỷ lệ trên doanh thu của mọi khách đã phân nhóm (%)
 * @param deliveredOrders số đơn Đã giao của khách trong nhóm
 * @param avgOrderValue giá trị trung bình mỗi đơn (VNĐ, làm tròn)
 * @param avgPurchaseIntervalDays nhịp mua trung bình (ngày giữa 2 đơn) của khách mua từ 2 lần trở
 *     lên; null nếu nhóm chưa ai mua lại
 * @param repeatCustomers số khách mua từ 2 đơn trở lên
 * @param avgTenureDays thời gian gắn bó trung bình: số ngày từ đơn Đã giao đầu tiên đến nay
 * @param topCategories tối đa 5 danh mục mua nhiều nhất
 * @param genders số khách theo giới tính
 * @param ageGroups số khách theo nhóm tuổi
 * @param careCount số khách cần chăm sóc (BR-43)
 * @param careList tối đa 20 khách cần chăm sóc, quá nhịp mua nhiều nhất trước
 */
public record SegmentProfileResponse(
    String code,
    String name,
    String description,
    long customerCount,
    double customerShare,
    long revenue,
    double revenueShare,
    long deliveredOrders,
    long avgOrderValue,
    Double avgPurchaseIntervalDays,
    long repeatCustomers,
    double avgTenureDays,
    List<CategoryShare> topCategories,
    List<Bucket> genders,
    List<Bucket> ageGroups,
    long careCount,
    List<CareCustomer> careList) {

  /**
   * Một danh mục và phần của nó trong số sản phẩm nhóm đã mua.
   *
   * @param name tên danh mục
   * @param quantity số sản phẩm đã mua
   * @param share tỷ lệ trên tổng số sản phẩm nhóm đã mua (%)
   */
  public record CategoryShare(String name, long quantity, double share) {}

  /**
   * Một nhóm con (giới tính hoặc độ tuổi).
   *
   * @param key mã nhóm con, vd FEMALE, 25_34, UNKNOWN
   * @param count số khách
   * @param share tỷ lệ trên số khách của nhóm (%)
   */
  public record Bucket(String key, long count, double share) {}

  /**
   * Một khách đã quá nhịp mua quen thuộc của chính họ (BR-43).
   *
   * @param accountId mã tài khoản
   * @param fullName họ tên
   * @param email email
   * @param orders số đơn Đã giao
   * @param lastOrderAt lúc đặt đơn Đã giao gần nhất
   * @param purchaseIntervalDays nhịp mua riêng (ngày giữa 2 đơn, làm tròn 1 chữ số)
   * @param daysSinceLastOrder số ngày từ đơn gần nhất đến nay
   */
  public record CareCustomer(
      long accountId,
      String fullName,
      String email,
      int orders,
      LocalDateTime lastOrderAt,
      double purchaseIntervalDays,
      long daysSinceLastOrder) {}
}
