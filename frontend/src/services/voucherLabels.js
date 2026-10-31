import { formatPrice } from './format';

// Nhãn tiếng Việt cho mã giảm giá (US-21, US-22).

/** Tình trạng mã do backend tính theo giờ Việt Nam. */
export const VOUCHER_STATUS_LABELS = {
  ACTIVE: 'Đang áp dụng',
  SCHEDULED: 'Chưa tới ngày',
  EXPIRED: 'Đã hết hạn',
  USED_UP: 'Đã hết lượt',
  DISABLED: 'Đã ngừng',
};

/** Mức giảm: "Giảm 10%, tối đa 20.000 ₫" hoặc "Giảm 30.000 ₫". */
export function describeDiscount(voucher) {
  if (voucher.discountType === 'PERCENT') {
    const cap = voucher.maxDiscountAmount
      ? `, tối đa ${formatPrice(voucher.maxDiscountAmount)}`
      : '';
    return `Giảm ${voucher.discountValue}%${cap}`;
  }
  return `Giảm ${formatPrice(voucher.discountValue)}`;
}

/** Số lượt: "3 / 100 lượt" hoặc "3 lượt (không giới hạn)", kèm giới hạn mỗi khách. */
export function describeUsage(voucher) {
  const total =
    voucher.usageLimitTotal == null
      ? `${voucher.usedCount} lượt (không giới hạn)`
      : `${voucher.usedCount} / ${voucher.usageLimitTotal} lượt`;
  return voucher.usageLimitPerAccount == null
    ? total
    : `${total}, mỗi khách ${voucher.usageLimitPerAccount}`;
}
