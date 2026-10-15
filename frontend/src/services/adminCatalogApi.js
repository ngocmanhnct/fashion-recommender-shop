import apiClient from './apiClient';

// Danh mục và thương hiệu có cùng dạng API (chỉ có tên), nên dùng chung 1 hàm tạo.
function createNamedResourceApi(basePath) {
  return {
    create: (name) => apiClient.post(basePath, { name }).then((res) => res.data),
    rename: (id, name) => apiClient.put(`${basePath}/${id}`, { name }).then((res) => res.data),
    remove: (id) => apiClient.delete(`${basePath}/${id}`),
  };
}

export const categoryAdminApi = createNamedResourceApi('/api/admin/categories');
export const brandAdminApi = createNamedResourceApi('/api/admin/brands');
