package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Refund;
import com.manhdn.fashionshop.entity.RefundStatus;
import java.time.LocalDateTime;

/**
 * Trạng thái hoàn tiền của đơn đã hủy (BR-28).
 *
 * @param status Đang hoàn tiền, Đã hoàn tiền hoặc Hoàn tiền thất bại
 * @param amount số tiền hoàn
 * @param initiatedAt thời điểm khởi tạo
 * @param completedAt thời điểm hoàn xong, null nếu chưa xong
 * @param failureReason lý do thất bại gần nhất, null nếu không có
 */
public record RefundResponse(
    RefundStatus status,
    long amount,
    LocalDateTime initiatedAt,
    LocalDateTime completedAt,
    String failureReason) {

  /** Chuyển entity sang DTO, null nếu đơn không có lần hoàn tiền nào. */
  public static RefundResponse from(Refund refund) {
    if (refund == null) {
      return null;
    }
    return new RefundResponse(
        refund.getStatus(),
        refund.getAmount(),
        refund.getInitiatedAt(),
        refund.getCompletedAt(),
        refund.getFailureReason());
  }
}
