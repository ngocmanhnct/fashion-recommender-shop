const VND = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });

export function formatPrice(value) {
  return value == null ? '—' : VND.format(value);
}

/** Khoảng giá của sản phẩm nhiều biến thể: "100.000 ₫" hoặc "100.000 ₫ – 150.000 ₫". */
export function formatPriceRange(min, max) {
  if (min == null) return '—';
  return min === max ? formatPrice(min) : `${formatPrice(min)} – ${formatPrice(max)}`;
}
