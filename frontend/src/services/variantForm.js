// Chuyển qua lại giữa dữ liệu biến thể của API và dữ liệu trong ô nhập (ô nhập luôn là chuỗi).

export const EMPTY_VARIANT = { size: '', color: '', price: '', stock: '0', skuCode: '' };

export function toVariantForm(variant) {
  return {
    size: variant.size ?? '',
    color: variant.color ?? '',
    price: String(variant.price ?? ''),
    stock: String(variant.stock ?? 0),
    skuCode: variant.skuCode ?? '',
  };
}

export function toVariantPayload(form) {
  const blankToNull = (value) => (value.trim() === '' ? null : value.trim());
  return {
    size: blankToNull(form.size),
    color: blankToNull(form.color),
    price: form.price === '' ? null : Number(form.price),
    stock: form.stock === '' ? null : Number(form.stock),
    skuCode: blankToNull(form.skuCode),
  };
}
