package com.manhdn.fashionshop.dto;

/**
 * Kết quả trang trả về sau khi thanh toán VNPay (US-10).
 *
 * @param orderId mã đơn, null nếu dữ liệu không hợp lệ
 * @param paid đơn đã được ghi nhận thanh toán thành công
 * @param message thông báo cho khách
 */
public record VnPayReturnResponse(Long orderId, boolean paid, String message) {}
