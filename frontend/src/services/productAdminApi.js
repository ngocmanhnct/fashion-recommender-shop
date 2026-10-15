import apiClient from './apiClient';

const BASE = '/api/admin/products';

export function searchProducts({ keyword, categoryId, includeDeleted, page, size = 20 }) {
  const params = { page, size, includeDeleted };
  if (keyword) params.keyword = keyword;
  if (categoryId) params.categoryId = categoryId;
  return apiClient.get(BASE, { params }).then((res) => res.data);
}

export function getProduct(id) {
  return apiClient.get(`${BASE}/${id}`).then((res) => res.data);
}

export function createProduct(payload) {
  return apiClient.post(BASE, payload).then((res) => res.data);
}

export function updateProduct(id, payload) {
  return apiClient.put(`${BASE}/${id}`, payload).then((res) => res.data);
}

export function deleteProduct(id) {
  return apiClient.delete(`${BASE}/${id}`);
}

export function addVariant(productId, payload) {
  return apiClient.post(`${BASE}/${productId}/variants`, payload).then((res) => res.data);
}

export function updateVariant(productId, variantId, payload) {
  return apiClient
    .put(`${BASE}/${productId}/variants/${variantId}`, payload)
    .then((res) => res.data);
}

export function deleteVariant(productId, variantId) {
  return apiClient.delete(`${BASE}/${productId}/variants/${variantId}`);
}
