import apiClient from './apiClient';

// Bảng điều khiển của Quản trị viên (US-28): doanh thu, đơn hàng, sản phẩm bán chạy, hiệu quả gợi ý.

/**
 * Số liệu từ ngày from đến ngày to (yyyy-MM-dd, theo giờ Việt Nam, tối đa 366 ngày):
 * { from, to, summary, days, ordersByStatus, topProducts, recommendations, abTest }.
 */
export function getDashboard({ from, to }) {
  return apiClient.get('/api/admin/dashboard', { params: { from, to } }).then((res) => res.data);
}
