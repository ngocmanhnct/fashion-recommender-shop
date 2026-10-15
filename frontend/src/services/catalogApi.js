import apiClient from './apiClient';

// API công khai, không cần đăng nhập.

export function getCategories() {
  return apiClient.get('/api/categories').then((res) => res.data);
}

export function getBrands() {
  return apiClient.get('/api/brands').then((res) => res.data);
}
