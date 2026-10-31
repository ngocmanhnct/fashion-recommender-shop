package com.manhdn.fashionshop.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kết quả phân nhóm khách hàng cho trang quản trị (US-19).
 *
 * @param model lần huấn luyện tạo ra các nhóm, null nếu chưa huấn luyện lần nào
 * @param segments các nhóm, từ giá trị cao đến thấp
 * @param segmentedCount số khách đã được phân nhóm
 * @param unsegmentedCount số khách chưa có đơn Đã giao nên chưa được phân nhóm (BR-24)
 */
public record SegmentOverviewResponse(
    ModelInfo model, List<SegmentStats> segments, long segmentedCount, long unsegmentedCount) {

  /**
   * Thông tin lần huấn luyện (BR-23).
   *
   * @param clusterCount số nhóm K đã chọn
   * @param elbowClusterCount K tại điểm gãy của đường inertia (Elbow)
   * @param customerCount số khách dùng để huấn luyện
   * @param silhouette chỉ số Silhouette của K đã chọn (KPI ≥ 0,3)
   * @param trainedAt thời điểm huấn luyện
   */
  public record ModelInfo(
      int clusterCount,
      int elbowClusterCount,
      int customerCount,
      double silhouette,
      LocalDateTime trainedAt) {}

  /**
   * Một nhóm và số liệu hiện tại của nhóm.
   *
   * @param code mã nhóm
   * @param name tên nhóm
   * @param description ý nghĩa, số liệu lúc huấn luyện và gợi ý chăm sóc
   * @param customerCount số khách hiện thuộc nhóm
   * @param share tỷ lệ trên số khách đã phân nhóm (%), làm tròn 1 chữ số
   * @param avgRecency R trung bình (ngày)
   * @param avgFrequency F trung bình (đơn)
   * @param avgMonetary M trung bình (VNĐ, làm tròn)
   * @param revenue tổng tiền các đơn Đã giao của khách hiện thuộc nhóm (VNĐ)
   * @param revenueShare tỷ lệ trên doanh thu của mọi khách đã phân nhóm (%), làm tròn 1 chữ số
   */
  public record SegmentStats(
      String code,
      String name,
      String description,
      long customerCount,
      double share,
      double avgRecency,
      double avgFrequency,
      long avgMonetary,
      long revenue,
      double revenueShare) {}
}
