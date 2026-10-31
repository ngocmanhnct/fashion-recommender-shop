import apiClient from './apiClient';

// API danh sách yêu thích (US-14), chỉ nhận token của khách hàng. Thêm và bỏ trả về mã các sản
// phẩm còn trong danh sách sau thao tác; gọi lại nhiều lần vẫn cho cùng kết quả.
const BASE = '/api/wishlist';

/** Sản phẩm còn bán trong danh sách yêu thích, thêm sau đứng trước: [{ product, addedAt }]. */
export function getWishlist() {
  return apiClient.get(BASE).then((res) => res.data);
}

/** Mã các sản phẩm trong danh sách yêu thích, để tô nút yêu thích. */
export function getWishlistIds() {
  return apiClient.get(`${BASE}/ids`).then((res) => res.data);
}

export function addToWishlist(productId) {
  return apiClient.put(`${BASE}/${productId}`).then((res) => res.data);
}

export function removeFromWishlist(productId) {
  return apiClient.delete(`${BASE}/${productId}`).then((res) => res.data);
}
