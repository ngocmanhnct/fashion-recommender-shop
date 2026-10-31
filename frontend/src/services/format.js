const VND = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });

export function formatPrice(value) {
  return value == null ? '—' : VND.format(value);
}

/** Khoảng giá của sản phẩm nhiều biến thể: "100.000 ₫" hoặc "100.000 ₫ – 150.000 ₫". */
export function formatPriceRange(min, max) {
  if (min == null) return '—';
  return min === max ? formatPrice(min) : `${formatPrice(min)} – ${formatPrice(max)}`;
}

const NUMBER = new Intl.NumberFormat('vi-VN');
const RATING = new Intl.NumberFormat('vi-VN', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

/** Số đếm có dấu chấm ngăn cách hàng nghìn, vd "1.050". */
export function formatNumber(value) {
  return NUMBER.format(value);
}

/** Điểm đánh giá trung bình 1 chữ số thập phân, vd "4,5". */
export function formatRating(value) {
  return RATING.format(value);
}

/** Khoảng thời gian đã qua tính bằng giây: "vừa xong", "5 phút trước", "2 giờ trước", "3 ngày trước". */
export function formatSecondsAgo(seconds) {
  if (seconds < 60) return 'vừa xong';
  if (seconds < 3600) return `${Math.floor(seconds / 60)} phút trước`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} giờ trước`;
  return `${Math.floor(seconds / 86400)} ngày trước`;
}
