package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.DiscountType;
import com.manhdn.fashionshop.entity.Voucher;
import java.time.LocalDateTime;

/**
 * Mã giảm giá cho trang quản trị (US-22).
 *
 * @param id mã số
 * @param code mã khách nhập
 * @param discountType giảm theo phần trăm hay số tiền cố định
 * @param discountValue phần trăm hoặc số tiền
 * @param minOrderAmount tổng tiền hàng tối thiểu, null là không yêu cầu
 * @param maxDiscountAmount trần giảm của mã phần trăm, null là không giới hạn
 * @param validFrom bắt đầu áp dụng (giờ Việt Nam)
 * @param validUntil hết hạn (giờ Việt Nam)
 * @param usageLimitTotal tổng lượt dùng, null là không giới hạn
 * @param usageLimitPerAccount lượt mỗi khách, null là không giới hạn
 * @param usedCount số lượt đã dùng (đơn bị hủy được trả lượt, BR-33)
 * @param remaining số lượt còn lại, null nếu không giới hạn tổng
 * @param active admin còn bật mã không
 * @param status tình trạng hiện tại: ACTIVE, SCHEDULED, EXPIRED, USED_UP hoặc DISABLED
 * @param createdAt thời điểm tạo
 */
public record VoucherResponse(
    long id,
    String code,
    DiscountType discountType,
    long discountValue,
    Long minOrderAmount,
    Long maxDiscountAmount,
    LocalDateTime validFrom,
    LocalDateTime validUntil,
    Integer usageLimitTotal,
    Integer usageLimitPerAccount,
    int usedCount,
    Integer remaining,
    boolean active,
    String status,
    LocalDateTime createdAt) {

  /** Chuyển entity sang DTO; now là giờ Việt Nam hiện tại để tính tình trạng. */
  public static VoucherResponse from(Voucher v, LocalDateTime now) {
    return new VoucherResponse(
        v.getId(),
        v.getCode(),
        v.getDiscountType(),
        v.getDiscountValue(),
        v.getMinOrderAmount(),
        v.getMaxDiscountAmount(),
        v.getValidFrom(),
        v.getValidUntil(),
        v.getUsageLimitTotal(),
        v.getUsageLimitPerAccount(),
        v.getUsedCount(),
        v.getUsageLimitTotal() == null
            ? null
            : Math.max(0, v.getUsageLimitTotal() - v.getUsedCount()),
        v.isActive(),
        statusOf(v, now),
        v.getCreatedAt());
  }

  private static String statusOf(Voucher v, LocalDateTime now) {
    if (!v.isActive()) {
      return "DISABLED";
    }
    if (now.isAfter(v.getValidUntil())) {
      return "EXPIRED";
    }
    if (!v.hasTotalUsesLeft()) {
      return "USED_UP";
    }
    return now.isBefore(v.getValidFrom()) ? "SCHEDULED" : "ACTIVE";
  }
}
