import apiClient from './apiClient';

// API xem khách hàng (US-18) và kết quả phân nhóm (US-19) của Quản trị viên.
const BASE = '/api/admin';

/** Giá trị lọc "chưa được phân nhóm" (BR-24) của tham số segment. */
export const UNSEGMENTED = 'NONE';

/** Một trang khách hàng, mới đăng ký trước; segment là mã nhóm hoặc UNSEGMENTED. */
export function searchCustomers({ keyword, segment, page = 0, size = 20 }) {
  return apiClient
    .get(`${BASE}/customers`, {
      params: { keyword: keyword || undefined, segment: segment || undefined, page, size },
    })
    .then((res) => res.data);
}

/** Chi tiết khách: thông tin, thống kê đơn, nhóm hiện tại (segment null nếu chưa phân nhóm). */
export function getCustomer(id) {
  return apiClient.get(`${BASE}/customers/${id}`).then((res) => res.data);
}

/** Một trang đơn hàng của khách, mới nhất trước. */
export function getCustomerOrders(id, page = 0, size = 10) {
  return apiClient
    .get(`${BASE}/customers/${id}/orders`, { params: { page, size } })
    .then((res) => res.data);
}

/** Kết quả phân nhóm: { model, segments, segmentedCount, unsegmentedCount }. */
export function getSegments() {
  return apiClient.get(`${BASE}/segments`).then((res) => res.data);
}
