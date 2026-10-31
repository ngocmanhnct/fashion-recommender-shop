import apiClient from './apiClient';

// Hiệu quả gợi ý cho Quản trị viên: thử nghiệm A/B gợi ý ở trang chủ (US-27) và tỷ lệ bấm theo vị
// trí, nguồn.

/** Danh sách thử nghiệm, mới nhất trước: [{ id, name, treatmentPercent, startedAt, endedAt, running }]. */
export function listAbTests() {
  return apiClient.get('/api/admin/ab-tests').then((res) => res.data);
}

/** Bắt đầu thử nghiệm mới; 409 khi đang có thử nghiệm chạy. */
export function startAbTest({ name, treatmentPercent }) {
  return apiClient.post('/api/admin/ab-tests', { name, treatmentPercent }).then((res) => res.data);
}

/** Dừng thử nghiệm. */
export function stopAbTest(id) {
  return apiClient.post(`/api/admin/ab-tests/${id}/stop`).then((res) => res.data);
}

/** Kết quả: { experiment, treatment, control, clickRate, orderRate, boughtClickedRate, ... }. */
export function getAbTestResult(id) {
  return apiClient.get(`/api/admin/ab-tests/${id}`).then((res) => res.data);
}

/** Tỷ lệ bấm gợi ý từ ngày from đến ngày to (yyyy-MM-dd): { from, to, total, rows }. */
export function getRecommendationCtr({ from, to }) {
  return apiClient
    .get('/api/admin/recommendations/ctr', { params: { from, to } })
    .then((res) => res.data);
}
