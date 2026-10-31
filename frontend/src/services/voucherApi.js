import apiClient from './apiClient';

// Mã giảm giá phía khách hàng (US-26): các mã riêng khách được tặng qua chiến dịch theo nhóm.

/**
 * Mã riêng khách được tặng và còn dùng được, hết hạn sớm nhất trước:
 * [{ code, discountType, discountValue, maxDiscountAmount, minOrderAmount, validUntil }].
 */
export function getMyVouchers() {
  return apiClient.get('/api/vouchers/me').then((res) => res.data);
}
