package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Xác nhận đặt hàng toàn bộ giỏ hàng (US-10).
 *
 * @param idempotencyKey mã định danh lần bấm xác nhận, trình duyệt sinh khi vào trang thanh toán;
 *     gửi lại cùng mã thì nhận lại đúng đơn đã tạo, không tạo đơn mới (BR-10)
 * @param paymentMethod phương thức thanh toán: COD hoặc ONLINE (VNPay)
 * @param addressId địa chỉ giao hàng trong sổ địa chỉ
 * @param expectedTotal tổng thanh toán khách đã thấy ở bước xem trước; khác tổng hiện tại thì từ
 *     chối để khách xem lại
 * @param voucherCode mã giảm giá đã áp ở bước xem trước, null hoặc rỗng nếu không dùng
 */
public record PlaceOrderRequest(
    @NotBlank(message = "Thiếu mã định danh lần đặt hàng")
        @Size(min = 8, max = 100, message = "Mã định danh lần đặt hàng dài 8 đến 100 ký tự")
        String idempotencyKey,
    @NotNull(message = "Chưa chọn phương thức thanh toán") PaymentMethod paymentMethod,
    @NotNull(message = "Chưa chọn địa chỉ giao hàng") Long addressId,
    @NotNull(message = "Thiếu tổng tiền đã xem") @PositiveOrZero Long expectedTotal,
    @Size(max = 50, message = "Mã giảm giá tối đa 50 ký tự") String voucherCode) {}
