import apiClient from './apiClient';

// API giỏ hàng (US-06, US-07). Mọi API trả về toàn bộ giỏ hàng sau thao tác:
// { items, notices, totalQuantity, subtotal }. Riêng /preview công khai, cho giỏ hàng lưu ở
// trình duyệt của khách chưa đăng nhập; các API còn lại chỉ nhận token của khách hàng.
const BASE = '/api/cart';

export function getCart() {
  return apiClient.get(BASE).then((res) => res.data);
}

export function addCartItem(variantId, quantity) {
  return apiClient.post(`${BASE}/items`, { variantId, quantity }).then((res) => res.data);
}

export function updateCartItem(variantId, quantity) {
  return apiClient.put(`${BASE}/items/${variantId}`, { quantity }).then((res) => res.data);
}

export function removeCartItem(variantId) {
  return apiClient.delete(`${BASE}/items/${variantId}`).then((res) => res.data);
}

/** Gộp giỏ hàng của trình duyệt vào giỏ của tài khoản vừa đăng nhập (BR-07). */
export function mergeCart(items) {
  return apiClient.post(`${BASE}/merge`, { items }).then((res) => res.data);
}

/** Giá và tồn kho hiện tại cho giỏ hàng của khách chưa đăng nhập, không lưu gì (BR-06). */
export function previewCart(items) {
  return apiClient.post(`${BASE}/preview`, { items }).then((res) => res.data);
}
