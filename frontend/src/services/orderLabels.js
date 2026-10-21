// Nhãn tiếng Việt cho trạng thái đơn hàng (BR-12) và phương thức thanh toán.

export const ORDER_STATUS_LABELS = {
  PENDING: 'Chờ xử lý',
  PROCESSING: 'Đang xử lý',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
};

export const PAYMENT_METHOD_LABELS = {
  COD: 'Thanh toán khi nhận hàng',
  ONLINE: 'Thanh toán online',
};

const DATE = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});
const TIME = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' });

/** Thời điểm backend trả về (LocalDateTime, giờ máy chủ) dạng "21/10/2026 14:05". */
export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return `${DATE.format(date)} ${TIME.format(date)}`;
}
