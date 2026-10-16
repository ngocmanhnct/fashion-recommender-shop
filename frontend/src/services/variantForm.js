// Chuyển qua lại giữa dữ liệu biến thể của API và dữ liệu trong ô nhập (ô nhập luôn là chuỗi).

export const EMPTY_VARIANT = {
  option1Value: '',
  option2Value: '',
  price: '',
  stock: '0',
  skuCode: '',
};

export function toVariantForm(variant) {
  return {
    option1Value: variant.option1Value ?? '',
    option2Value: variant.option2Value ?? '',
    price: String(variant.price ?? ''),
    stock: String(variant.stock ?? 0),
    skuCode: variant.skuCode ?? '',
  };
}

// variationCount: số tầng phân loại của sản phẩm; giá trị ở tầng không dùng gửi null.
export function toVariantPayload(form, variationCount) {
  const blankToNull = (value) => (value.trim() === '' ? null : value.trim());
  return {
    option1Value: variationCount >= 1 ? blankToNull(form.option1Value) : null,
    option2Value: variationCount >= 2 ? blankToNull(form.option2Value) : null,
    price: form.price === '' ? null : Number(form.price),
    stock: form.stock === '' ? null : Number(form.stock),
    skuCode: blankToNull(form.skuCode),
  };
}

// Nhãn phân loại như backend lưu ở dòng đơn (BR-15), vd "Đen – 42".
export function variantLabel(variant) {
  return [variant.option1Value, variant.option2Value].filter(Boolean).join(' – ');
}
