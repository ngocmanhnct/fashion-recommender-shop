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

/**
 * Hồ sơ 1 nhóm (US-25): doanh thu, nhịp mua, danh mục mua nhiều, giới tính, độ tuổi và khách cần
 * chăm sóc (BR-43).
 */
export function getSegmentProfile(code) {
  return apiClient.get(`${BASE}/segments/${encodeURIComponent(code)}`).then((res) => res.data);
}

/**
 * Tặng mã giảm giá riêng cho một nhóm (US-26): target ALL = cả nhóm, CARE = khách cần chăm sóc,
 * AUTO = tự tặng cho khách mới vào nhóm (US-24, kèm validDays: số ngày mỗi khách được dùng mã);
 * voucher là điều kiện mã như khi tạo mã thường. Trả về chiến dịch vừa tạo.
 */
export function createCampaign(code, { target, voucher, validDays }) {
  return apiClient
    .post(`${BASE}/segments/${encodeURIComponent(code)}/campaigns`, { target, voucher, validDays })
    .then((res) => res.data);
}

/** Dừng chương trình tự tặng (US-24); khách đã nhận vẫn dùng được mã. */
export function stopCampaign(id) {
  return apiClient.post(`${BASE}/campaigns/${id}/stop`).then((res) => res.data);
}

/** Các chiến dịch đã tặng, mới nhất trước, kèm kết quả; segment bỏ trống là mọi nhóm. */
export function getCampaigns(segment) {
  return apiClient
    .get(`${BASE}/campaigns`, { params: { segment: segment || undefined } })
    .then((res) => res.data);
}
