// Chuyển qua lại giữa thông tin sản phẩm của API và dữ liệu trong ô nhập.

export const EMPTY_PRODUCT_INFO = {
  name: '',
  description: '',
  categoryId: '',
  brandId: '',
  imageUrls: '',
  variationName1: '',
  variationName2: '',
};

export function toProductInfoForm(product) {
  return {
    name: product.name,
    description: product.description ?? '',
    categoryId: String(product.category.id),
    brandId: product.brand ? String(product.brand.id) : '',
    imageUrls: product.imageUrls.join('\n'),
    variationName1: product.variationName1 ?? '',
    variationName2: product.variationName2 ?? '',
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
    variationName1: info.variationName1.trim() || null,
    variationName2: info.variationName2.trim() || null,
  };
}

// Tên các tầng phân loại đang dùng (tối đa 2, BR-05). Tầng 2 chỉ tính khi có tầng 1, giống backend.
export function variationNamesOf(source) {
  const first = (source.variationName1 ?? '').trim();
  const second = (source.variationName2 ?? '').trim();
  if (!first) return [];
  return second ? [first, second] : [first];
}
