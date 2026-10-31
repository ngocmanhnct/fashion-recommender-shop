import apiClient from './apiClient';

// API xem sản phẩm cho khách: công khai, không cần đăng nhập. Riêng ghi lượt xem chỉ nhận token
// của khách hàng (CUSTOMER).
const BASE = '/api/products';

/** Danh sách sản phẩm đang bán (lọc, sắp xếp, phân trang), trả về PageResponse. */
export function searchProducts(params) {
  return apiClient.get(BASE, { params }).then((res) => res.data);
}

/** Tối đa 4 sản phẩm gợi ý khi tìm không có kết quả (US-02). */
export function getProductSuggestions(keyword) {
  const params = keyword ? { keyword } : {};
  return apiClient.get(`${BASE}/suggestions`, { params }).then((res) => res.data);
}

export function getProduct(id) {
  return apiClient.get(`${BASE}/${id}`).then((res) => res.data);
}

/** Tối đa 4 sản phẩm liên quan, mỗi sản phẩm kèm lý do gợi ý (BR-21). */
export function getRelatedProducts(id) {
  return apiClient.get(`${BASE}/${id}/related`).then((res) => res.data);
}

/**
 * "Thường được mua cùng" các sản phẩm trong giỏ (chức năng 20): tối đa 4 sản phẩm kèm lý do, rỗng
 * khi chưa ai mua cùng. Mã gửi dạng "1,2" vì axios mặc định gửi mảng thành productIds[]=1.
 */
export function getBoughtTogether(productIds) {
  return apiClient
    .get(`${BASE}/bought-together`, { params: { productIds: productIds.join(',') } })
    .then((res) => res.data);
}

/** Ghi 1 lượt xem sản phẩm của khách hàng đang đăng nhập (US-03, BR-19). */
export function recordProductView(id) {
  return apiClient.post(`${BASE}/${id}/views`);
}
