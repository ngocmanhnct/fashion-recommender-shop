import apiClient from './apiClient';

// API quản lý đơn hàng của Quản trị viên (US-17).
const BASE = '/api/admin/orders';

/** Một trang đơn hàng, mới nhất trước; status rỗng là mọi trạng thái. */
export function searchOrders({ status, keyword, page = 0, size = 20 }) {
  return apiClient
    .get(BASE, {
      params: { status: status || undefined, keyword: keyword || undefined, page, size },
    })
    .then((res) => res.data);
}

/** Chi tiết đơn: { order, customerId, customerName, customerEmail, nextStatus, adminCancellable }. */
export function getAdminOrder(id) {
  return apiClient.get(`${BASE}/${id}`).then((res) => res.data);
}

/** Chuyển đơn sang trạng thái kế tiếp hoặc CANCELLED. */
export function changeOrderStatus(id, status) {
  return apiClient.put(`${BASE}/${id}/status`, { status }).then((res) => res.data);
}
