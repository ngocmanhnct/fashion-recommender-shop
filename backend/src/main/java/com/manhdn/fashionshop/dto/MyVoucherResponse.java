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
 * @param validUntil hạn dùng của khách (giờ Việt Nam): hạn của mã, hoặc hạn riêng tính từ lúc nhận
 *     nếu sớm hơn (US-24)
 */
public record MyVoucherResponse(
    String code,
    DiscountType discountType,
    long discountValue,
    Long maxDiscountAmount,
    Long minOrderAmount,
    LocalDateTime validUntil) {

  /** Tạo từ entity, với hạn dùng riêng của khách. */
  public static MyVoucherResponse from(Voucher v, LocalDateTime validUntil) {
    return new MyVoucherResponse(
        v.getCode(),
        v.getDiscountType(),
        v.getDiscountValue(),
        v.getMaxDiscountAmount(),
        v.getMinOrderAmount(),
        validUntil);
  }
}
