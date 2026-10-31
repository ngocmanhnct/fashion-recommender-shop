package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.DiscountType;
import com.manhdn.fashionshop.entity.Voucher;
import java.time.LocalDateTime;

/**
 * Một mã giảm giá riêng khách được tặng và còn dùng được (US-26, trang "Ưu đãi của bạn").
 *
 * @param code mã để nhập ở trang thanh toán
 * @param discountType giảm theo phần trăm hay số tiền cố định
 * @param discountValue phần trăm hoặc số tiền giảm
 * @param maxDiscountAmount trần giảm của mã phần trăm, null nếu không trần
 * @param minOrderAmount đơn tối thiểu, null nếu không yêu cầu
 * @param validUntil hạn dùng (giờ Việt Nam)
 */
public record MyVoucherResponse(
    String code,
    DiscountType discountType,
    long discountValue,
    Long maxDiscountAmount,
    Long minOrderAmount,
    LocalDateTime validUntil) {

  /** Tạo từ entity. */
  public static MyVoucherResponse from(Voucher v) {
    return new MyVoucherResponse(
        v.getCode(),
        v.getDiscountType(),
        v.getDiscountValue(),
        v.getMaxDiscountAmount(),
        v.getMinOrderAmount(),
        v.getValidUntil());
  }
}
