package com.manhdn.fashionshop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.manhdn.fashionshop.repository.AbAssignmentRepository.VariantStats;

/**
 * Kết quả một thử nghiệm A/B gợi ý ở trang chủ (US-27, BR-46): số liệu của 2 nhóm và phép so sánh
 * từng tỷ lệ theo khách.
 *
 * @param experiment thông tin thử nghiệm
 * @param treatment nhóm thấy gợi ý của ml-service
 * @param control nhóm thấy sản phẩm bán chạy
 * @param clickRate so sánh tỷ lệ khách bấm gợi ý ít nhất 1 lần
 * @param orderRate so sánh tỷ lệ khách đặt hàng
 * @param boughtClickedRate so sánh tỷ lệ khách mua sản phẩm đã bấm từ gợi ý
 * @param minUsersPerGroup số khách tối thiểu mỗi nhóm trước khi kết luận
 */
public record AbTestResultResponse(
    AbTestResponse experiment,
    Group treatment,
    Group control,
    Comparison clickRate,
    Comparison orderRate,
    Comparison boughtClickedRate,
    int minUsersPerGroup) {

  /**
   * Số liệu của một nhóm. Tỷ lệ tính theo phần trăm, làm tròn 2 chữ số; null khi mẫu số bằng 0.
   *
   * @param users số khách trong nhóm
   * @param shown lượt hiện sản phẩm gợi ý ở trang chủ
   * @param clicks lượt bấm
   * @param ctr lượt bấm ÷ lượt hiện × 100
   * @param usersClicked số khách bấm ít nhất 1 lần
   * @param clickRate usersClicked ÷ users × 100
   * @param usersOrdered số khách đặt ít nhất 1 đơn chưa hủy
   * @param orderRate usersOrdered ÷ users × 100
   * @param orders số đơn chưa hủy
   * @param revenue tổng tiền các đơn (VNĐ)
   * @param revenuePerUser revenue ÷ users, làm tròn xuống (VNĐ); null khi chưa có khách
   * @param usersBoughtClicked số khách mua sản phẩm họ đã bấm từ gợi ý trang chủ
   * @param boughtClickedRate usersBoughtClicked ÷ users × 100
   */
  public record Group(
      long users,
      long shown,
      long clicks,
      Double ctr,
      long usersClicked,
      Double clickRate,
      long usersOrdered,
      Double orderRate,
      long orders,
      long revenue,
      Long revenuePerUser,
      long usersBoughtClicked,
      Double boughtClickedRate) {

    /** Tạo số liệu nhóm và tính các tỷ lệ. */
    public static Group of(VariantStats s) {
      return new Group(
          s.users(),
          s.shown(),
          s.clicks(),
          percent(s.clicks(), s.shown()),
          s.usersClicked(),
          percent(s.usersClicked(), s.users()),
          s.usersOrdered(),
          percent(s.usersOrdered(), s.users()),
          s.orders(),
          s.revenue(),
          s.users() == 0 ? null : s.revenue() / s.users(),
          s.usersBoughtClicked(),
          percent(s.usersBoughtClicked(), s.users()));
    }

    private static Double percent(long part, long whole) {
      return whole == 0 ? null : Math.round(part * 10000.0 / whole) / 100.0;
    }
  }

  /**
   * So sánh một tỷ lệ của 2 nhóm bằng kiểm định z hai tỷ lệ, hai phía.
   *
   * @param difference tỷ lệ nhóm gợi ý trừ tỷ lệ nhóm bán chạy, điểm phần trăm, làm tròn 2 chữ số;
   *     null khi một nhóm chưa có khách
   * @param probability giá trị p (trong JSON là pValue), làm tròn 4 chữ số; null khi một nhóm chưa
   *     có khách
   * @param verdict NOT_ENOUGH_DATA (nhóm nhỏ hơn mức tối thiểu), TREATMENT_BETTER, CONTROL_BETTER
   *     (khác biệt có ý nghĩa ở mức 5%) hoặc NO_DIFFERENCE
   */
  public record Comparison(
      Double difference, @JsonProperty("pValue") Double probability, String verdict) {}
}
