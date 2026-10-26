import apiClient from './apiClient';

// Gợi ý cá nhân hóa ở trang chủ (US-13), chỉ cho khách hàng đã đăng nhập. Danh sách do ml-service
// tính sẵn; backend chỉ đọc bảng recommendation.

/**
 * Khối "Gợi ý cho bạn": { source, products: [{ product, reason }] }.
 * source: BEHAVIOR (theo hành vi), SURVEY (theo khảo sát), POPULAR (chưa có, là sản phẩm bán chạy).
 */
export function getMyRecommendations() {
  return apiClient.get('/api/recommendations/me').then((res) => res.data);
}
