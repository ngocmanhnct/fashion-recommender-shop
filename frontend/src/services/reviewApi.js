import apiClient from './apiClient';

// API đánh giá sản phẩm (US-15). Xem đánh giá công khai; xem và gửi đánh giá của mình chỉ nhận
// token của khách hàng.
const base = (productId) => `/api/products/${productId}/reviews`;

/** { ratingAverage, reviewCount, starCounts (1 sao đến 5 sao), reviews (PageResponse) }. */
export function getProductReviews(productId, page = 0, size = 5) {
  return apiClient.get(base(productId), { params: { page, size } }).then((res) => res.data);
}

/** { canReview (đã nhận hàng, BR-17), review (null nếu chưa đánh giá) }. */
export function getMyReview(productId) {
  return apiClient.get(`${base(productId)}/me`).then((res) => res.data);
}

/** Gửi hoặc sửa đánh giá (BR-18): { rating 1-5, comment }. */
export function saveMyReview(productId, review) {
  return apiClient.put(`${base(productId)}/me`, review).then((res) => res.data);
}
