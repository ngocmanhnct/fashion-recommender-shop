import apiClient from './apiClient';

// API quản lý mã giảm giá của Quản trị viên (US-22).
const BASE = '/api/admin/vouchers';

/** Một trang mã, mới tạo trước; keyword là một phần mã. */
export function searchVouchers({ keyword, page = 0, size = 20 }) {
  return apiClient
    .get(BASE, { params: { keyword: keyword || undefined, page, size } })
    .then((res) => res.data);
}

/** Tạo mã mới (đang bật). */
export function createVoucher(voucher) {
  return apiClient.post(BASE, voucher).then((res) => res.data);
}

/** Sửa điều kiện của mã; mã đã có người dùng thì không đổi được chuỗi mã. */
export function updateVoucher(id, voucher) {
  return apiClient.put(`${BASE}/${id}`, voucher).then((res) => res.data);
}

/** Bật lại hoặc ngừng áp dụng mã. */
export function setVoucherActive(id, active) {
  return apiClient.put(`${BASE}/${id}/active`, { active }).then((res) => res.data);
}
