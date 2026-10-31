package com.manhdn.fashionshop.dto;

import java.util.List;

/**
 * Tóm tắt đơn hàng trước khi xác nhận (US-10): giỏ hàng theo giá và tồn kho hiện tại, phí vận
 * chuyển (BR-35), mã giảm giá (US-21) và tổng thanh toán.
 *
 * @param items các dòng sẽ được đặt
 * @param notices các dòng hệ thống vừa tự loại hoặc giảm số lượng (BR-11)
 * @param subtotal tổng tiền hàng
 * @param shippingFee phí vận chuyển
 * @param discount số tiền giảm giá thực nhận (BR-32)
 * @param total tổng thanh toán, gửi lại khi xác nhận để phát hiện giá vừa đổi
 * @param voucherCode mã giảm giá đang áp dụng (chữ hoa), null nếu không có hoặc không dùng được
 * @param voucherError lý do mã khách nhập không dùng được, null nếu dùng được hoặc không nhập
 * @param onlinePaymentAvailable cửa hàng đã bật thanh toán online qua VNPay chưa
 */
public record CheckoutPreviewResponse(
    List<CartItemResponse> items,
    List<CartNotice> notices,
    long subtotal,
    long shippingFee,
    long discount,
    long total,
    String voucherCode,
    String voucherError,
    boolean onlinePaymentAvailable) {}
