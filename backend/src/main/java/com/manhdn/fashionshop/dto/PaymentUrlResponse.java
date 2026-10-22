package com.manhdn.fashionshop.dto;

import java.time.LocalDateTime;

/**
 * Đường dẫn chuyển khách sang trang thanh toán VNPay (US-10).
 *
 * @param paymentUrl đường dẫn đã ký
 * @param expiresAt thời điểm đường dẫn hết hiệu lực, giờ Việt Nam
 */
public record PaymentUrlResponse(String paymentUrl, LocalDateTime expiresAt) {}
