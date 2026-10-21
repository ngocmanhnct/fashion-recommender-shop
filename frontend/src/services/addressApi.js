import apiClient from './apiClient';

// API sổ địa chỉ giao hàng (US-09), chỉ cho khách hàng. Địa chỉ: { id, recipientName, phone,
// addressDetail, isDefault }; danh sách có địa chỉ mặc định đứng đầu.
const BASE = '/api/addresses';

export function listAddresses() {
  return apiClient.get(BASE).then((res) => res.data);
}

export function createAddress(address) {
  return apiClient.post(BASE, address).then((res) => res.data);
}

export function updateAddress(id, address) {
  return apiClient.put(`${BASE}/${id}`, address).then((res) => res.data);
}

export function deleteAddress(id) {
  return apiClient.delete(`${BASE}/${id}`);
}
