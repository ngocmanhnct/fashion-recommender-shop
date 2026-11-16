import apiClient from './apiClient';

// Hiệu quả gợi ý cho Quản trị viên (US-13): tỷ lệ bấm theo vị trí, nguồn.

/** Tỷ lệ bấm gợi ý từ ngày from đến ngày to (yyyy-MM-dd): { from, to, total, rows }. */
export function getRecommendationCtr({ from, to }) {
  return apiClient
    .get('/api/admin/recommendations/ctr', { params: { from, to } })
    .then((res) => res.data);
}
