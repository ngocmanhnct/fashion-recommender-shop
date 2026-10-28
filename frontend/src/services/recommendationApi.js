import apiClient from './apiClient';

// Gợi ý cá nhân hóa ở trang chủ (US-13), chỉ cho khách hàng đã đăng nhập. Danh sách do ml-service
// tính sẵn; backend chỉ đọc bảng recommendation. Kèm nhật ký lượt hiện, lượt bấm gợi ý (CTR).

/**
 * Khối "Gợi ý cho bạn": { source, products: [{ product, reason, source }] }.
 * source: BEHAVIOR (theo hành vi), SURVEY (theo khảo sát), POPULAR (chưa có, là sản phẩm bán chạy).
 */
export function getMyRecommendations() {
  return apiClient.get('/api/recommendations/me').then((res) => res.data);
}

/**
 * Ghi lượt hiện (type SHOWN, cả khối) hoặc lượt bấm (CLICKED, 1 sản phẩm) của gợi ý, công khai.
 * items: [{ productId, source, rank }]. Chỉ để thống kê: lỗi thì bỏ qua, không báo người dùng.
 */
export function logRecommendationEvent(type, placement, items) {
  return apiClient
    .post('/api/recommendations/events', { type, placement, items })
    .catch(() => undefined);
}
