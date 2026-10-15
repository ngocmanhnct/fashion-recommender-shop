// Chuyển qua lại giữa thông tin sản phẩm của API và dữ liệu trong ô nhập.

export const EMPTY_PRODUCT_INFO = {
  name: '',
  description: '',
  categoryId: '',
  brandId: '',
  imageUrls: '',
};

export function toProductInfoForm(product) {
  return {
    name: product.name,
    description: product.description ?? '',
    categoryId: String(product.category.id),
    brandId: product.brand ? String(product.brand.id) : '',
    imageUrls: product.imageUrls.join('\n'),
  };
}

export function toProductInfoPayload(info) {
  return {
    name: info.name.trim(),
    description: info.description.trim() || null,
    categoryId: info.categoryId ? Number(info.categoryId) : null,
    brandId: info.brandId ? Number(info.brandId) : null,
    imageUrls: info.imageUrls
      .split('\n')
      .map((url) => url.trim())
      .filter(Boolean),
  };
}
