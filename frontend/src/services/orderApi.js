import apiClient from './apiClient';

// API đặt hàng và đơn hàng của khách hàng (US-10, US-11, US-20).
const BASE = '/api/orders';

/** Tóm tắt trước khi đặt: { items, notices, subtotal, shippingFee, discount, total }. */
export function getCheckoutPreview() {
  return apiClient.get(`${BASE}/preview`).then((res) => res.data);
}

/**
 * Đặt toàn bộ giỏ hàng. Gửi lại cùng idempotencyKey thì nhận lại đúng đơn đã tạo (BR-10), nên bấm
 * lại sau khi mất mạng không tạo thêm đơn.
 */
export function placeOrder({ idempotencyKey, paymentMethod, addressId, expectedTotal }) {
  return apiClient
    .post(BASE, { idempotencyKey, paymentMethod, addressId, expectedTotal })
    .then((res) => res.data);
}

/** Một trang đơn hàng, mới nhất trước (PageResponse). */
export function listOrders(page = 0, size = 10) {
  return apiClient.get(BASE, { params: { page, size } }).then((res) => res.data);
}

export function getOrder(id) {
  return apiClient.get(`${BASE}/${id}`).then((res) => res.data);
}

export function cancelOrder(id) {
  return apiClient.post(`${BASE}/${id}/cancel`).then((res) => res.data);
}

/**
 * Mã định danh ngẫu nhiên (UUID v4) cho một lần đặt hàng. crypto.randomUUID chỉ có trên https và
 * localhost, nên trang chạy http (vd máy chủ thử) dùng crypto.getRandomValues.
 */
export function newIdempotencyKey() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
