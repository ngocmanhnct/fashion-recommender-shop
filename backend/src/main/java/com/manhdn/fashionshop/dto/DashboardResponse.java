package com.manhdn.fashionshop.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Trang tổng quan của Quản trị viên (US-28) trong một khoảng ngày theo giờ Việt Nam: kinh doanh và
 * hiệu quả gợi ý. Doanh thu chỉ tính đơn Đã giao (BR-47).
 *
 * @param from ngày đầu (tính cả ngày này)
 * @param to ngày cuối (tính cả ngày này)
 * @param summary các số tổng
 * @param days từng ngày trong khoảng, kể cả ngày không có đơn
 * @param ordersByStatus số đơn đặt trong khoảng theo trạng thái, đủ 5 trạng thái
 * @param topProducts sản phẩm bán chạy nhất theo số lượng ở đơn Đã giao
 * @param recommendations lượt hiện, lượt bấm và tỷ lệ bấm gợi ý
 * @param abTest thử nghiệm A/B đang chạy; null khi không có
 */
public record DashboardResponse(
    LocalDate from,
    LocalDate to,
    Summary summary,
    List<Day> days,
    List<StatusCount> ordersByStatus,
    List<TopProduct> topProducts,
    Recommendations recommendations,
    AbTestSummary abTest) {

  /**
   * Các số tổng của khoảng.
   *
   * @param revenue doanh thu: tổng tiền khách trả của đơn Đã giao (sau giảm giá, gồm phí vận
   *     chuyển), VNĐ
   * @param deliveredOrders số đơn Đã giao
   * @param orders số đơn đặt trong khoảng, mọi trạng thái
   * @param averageOrderValue revenue ÷ deliveredOrders, làm tròn xuống; null khi chưa có đơn
   * @param newCustomers số tài khoản khách hàng đăng ký trong khoảng
   * @param goodsRevenue tiền hàng của đơn Đã giao (trước giảm giá, không gồm phí vận chuyển)
   * @param recommendedRevenue tiền hàng của sản phẩm khách đã bấm từ gợi ý trong 7 ngày trước khi
   *     đặt, ở đơn Đã giao
   * @param recommendedShare recommendedRevenue ÷ goodsRevenue × 100, làm tròn 2 chữ số; null khi
   *     chưa có tiền hàng
   */
  public record Summary(
      long revenue,
      long deliveredOrders,
      long orders,
      Long averageOrderValue,
      long newCustomers,
      long goodsRevenue,
      long recommendedRevenue,
      Double recommendedShare) {}

  /**
   * Một ngày.
   *
   * @param date ngày (giờ Việt Nam)
   * @param revenue doanh thu của đơn Đã giao đặt trong ngày
   * @param deliveredOrders số đơn Đã giao đặt trong ngày
   * @param orders số đơn đặt trong ngày, mọi trạng thái
   */
  public record Day(LocalDate date, long revenue, long deliveredOrders, long orders) {}

  /**
   * Số đơn của một trạng thái.
   *
   * @param status trạng thái đơn
   * @param count số đơn
   */
  public record StatusCount(String status, long count) {}

  /**
   * Một sản phẩm bán chạy.
   *
   * @param productId mã sản phẩm
   * @param name tên hiện tại của sản phẩm
   * @param quantity số lượng đã bán ở đơn Đã giao
   * @param revenue tiền hàng của các dòng đơn đó
   */
  public record TopProduct(long productId, String name, long quantity, long revenue) {}

  /**
   * Hiệu quả gợi ý trong khoảng (mọi khách, kể cả khách chưa đăng nhập).
   *
   * @param total tổng mọi vị trí, nguồn
   * @param byPlacement theo vị trí khối gợi ý, xếp theo tên
   * @param bySource theo nguồn gợi ý, xếp theo tên
   */
  public record Recommendations(
      RecommendationCtrResponse.Row total,
      List<RecommendationCtrResponse.Row> byPlacement,
      List<RecommendationCtrResponse.Row> bySource) {}

  /**
   * Tóm tắt thử nghiệm A/B đang chạy (chi tiết ở trang Hiệu quả gợi ý).
   *
   * @param id mã thử nghiệm
   * @param name tên thử nghiệm
   * @param startedAt lúc bắt đầu
   * @param treatmentUsers số khách nhóm gợi ý học máy
   * @param treatmentClickRate tỷ lệ khách nhóm đó bấm gợi ý ít nhất 1 lần (%)
   * @param controlUsers số khách nhóm bán chạy
   * @param controlClickRate tỷ lệ khách nhóm đó bấm gợi ý ít nhất 1 lần (%)
   */
  public record AbTestSummary(
      long id,
      String name,
      LocalDateTime startedAt,
      long treatmentUsers,
      Double treatmentClickRate,
      long controlUsers,
      Double controlClickRate) {}
}
