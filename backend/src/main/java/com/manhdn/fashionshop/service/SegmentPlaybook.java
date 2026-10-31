package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.CampaignSuggestion;
import com.manhdn.fashionshop.entity.CampaignTarget;
import com.manhdn.fashionshop.entity.DiscountType;

/**
 * Cách chăm sóc từng kiểu nhóm khách (US-26), theo mã nhóm ml-service đặt (VIP, LOYAL, SAVER, NEW,
 * AT_RISK, LOST). Mức đơn tối thiểu của nhóm mua đều tính từ giá trị đơn trung bình của chính nhóm,
 * để mã kéo khách mua đơn lớn hơn thói quen. Các con số là gợi ý ban đầu (giả định), Quản trị viên
 * sửa được trước khi tặng.
 */
final class SegmentPlaybook {

  /** Mức làm tròn lên của đơn tối thiểu gợi ý. */
  static final long MIN_ORDER_STEP = 50_000;

  private SegmentPlaybook() {}

  /**
   * Gợi ý cho nhóm.
   *
   * @param code mã nhóm
   * @param avgOrderValue giá trị đơn trung bình của nhóm (VNĐ), 0 nếu chưa có đơn
   * @return mục tiêu, lý do và ưu đãi gợi ý
   */
  static CampaignSuggestion suggest(String code, long avgOrderValue) {
    return switch (code) {
      case "VIP" ->
          new CampaignSuggestion(
              goalOf(code),
              "Nhóm mang lại nhiều doanh thu nhất và vẫn mua đều. Mã nhỏ như lời cảm ơn là đủ,"
                  + " giảm sâu chỉ làm mất lợi nhuận.",
              CampaignTarget.ALL,
              DiscountType.PERCENT,
              10,
              200_000L,
              null,
              14);
      case "LOYAL" ->
          new CampaignSuggestion(
              goalOf(code),
              "Khách mua đều đặn. Mã cho đơn lớn hơn mức họ hay mua giúp mỗi lần mua mang về"
                  + " nhiều hơn.",
              CampaignTarget.ALL,
              DiscountType.FIXED,
              50_000,
              null,
              aboveAverage(avgOrderValue, 1.3),
              14);
      case "SAVER" ->
          new CampaignSuggestion(
              goalOf(code),
              "Khách mua khá thường xuyên nhưng đơn nhỏ. Mức tối thiểu cao hơn đơn quen thuộc kéo"
                  + " giá trị đơn lên.",
              CampaignTarget.ALL,
              DiscountType.FIXED,
              30_000,
              null,
              aboveAverage(avgOrderValue, 1.5),
              7);
      case "NEW" ->
          new CampaignSuggestion(
              goalOf(code),
              "Khách mới mua 1-2 đơn. Đơn thứ hai là lúc quyết định họ có thành khách quen hay"
                  + " không.",
              CampaignTarget.ALL,
              DiscountType.PERCENT,
              15,
              50_000L,
              null,
              14);
      case "AT_RISK" ->
          new CampaignSuggestion(
              goalOf(code),
              "Nhóm từng mua khá nhiều nhưng lâu chưa quay lại. Ưu tiên khách đã quá nhịp mua của"
                  + " chính họ; ưu đãi đủ mạnh, hạn ngắn để họ quyết định sớm.",
              CampaignTarget.CARE,
              DiscountType.PERCENT,
              20,
              100_000L,
              null,
              7);
      case "LOST" ->
          new CampaignSuggestion(
              goalOf(code),
              "Khách đã rất lâu không mua nên ít người quay lại. Tặng một lần, hạn ngắn, xem kết"
                  + " quả rồi mới tặng tiếp.",
              CampaignTarget.ALL,
              DiscountType.PERCENT,
              25,
              80_000L,
              null,
              7);
      default ->
          new CampaignSuggestion(
              goalOf(code),
              "Tặng ưu đãi nhỏ rồi xem bao nhiêu khách quay lại mua.",
              CampaignTarget.ALL,
              DiscountType.PERCENT,
              10,
              50_000L,
              null,
              14);
    };
  }

  /** Mục tiêu ngắn gọn của nhóm, hiện trên thẻ nhóm. */
  static String goalOf(String code) {
    return switch (code) {
      case "VIP" -> "Giữ chân nhóm mang lại nhiều doanh thu nhất";
      case "LOYAL" -> "Tăng giá trị mỗi đơn";
      case "SAVER" -> "Khuyến khích đơn lớn hơn";
      case "NEW" -> "Có đơn thứ hai";
      case "AT_RISK" -> "Kéo khách quay lại trước khi mất hẳn";
      case "LOST" -> "Thử kéo lại với chi phí thấp";
      default -> "Chăm sóc nhóm khách";
    };
  }

  /** Đơn tối thiểu bằng factor lần giá trị đơn trung bình, làm tròn lên bội 50.000đ. */
  static Long aboveAverage(long avgOrderValue, double factor) {
    if (avgOrderValue <= 0) {
      return null;
    }
    long target = (long) Math.ceil(avgOrderValue * factor);
    return (target + MIN_ORDER_STEP - 1) / MIN_ORDER_STEP * MIN_ORDER_STEP;
  }
}
