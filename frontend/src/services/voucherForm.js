// Chuyển đổi giữa mã giảm giá (API) và giá trị các ô của biểu mẫu mã (components/VoucherForm).

const DAY_MS = 24 * 60 * 60 * 1000;

/** Giờ địa phương dạng "yyyy-MM-ddTHH:mm" cho ô datetime-local. */
export function toInputTime(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}`;
}

function nowMinute() {
  const now = new Date();
  now.setSeconds(0, 0);
  return now;
}

const text = (value) => (value == null ? '' : String(value));

/** Biểu mẫu trống cho mã mới: giảm theo phần trăm, hạn 7 ngày, mỗi khách 1 lượt. */
export function emptyVoucherForm() {
  const now = nowMinute();
  return {
    code: '',
    discountType: 'PERCENT',
    discountValue: '',
    maxDiscountAmount: '',
    minOrderAmount: '',
    validFrom: toInputTime(now),
    validUntil: toInputTime(new Date(now.getTime() + 7 * DAY_MS)),
    usageLimitTotal: '',
    usageLimitPerAccount: '1',
  };
}

/** Biểu mẫu điền sẵn từ một mã đã có (để sửa). */
export function voucherFormOf(voucher) {
  return {
    code: voucher.code,
    discountType: voucher.discountType,
    discountValue: text(voucher.discountValue),
    maxDiscountAmount: text(voucher.maxDiscountAmount),
    minOrderAmount: text(voucher.minOrderAmount),
    validFrom: voucher.validFrom.slice(0, 16),
    validUntil: voucher.validUntil.slice(0, 16),
    usageLimitTotal: text(voucher.usageLimitTotal),
    usageLimitPerAccount: text(voucher.usageLimitPerAccount),
  };
}

/**
 * Biểu mẫu điền sẵn từ ưu đãi gợi ý cho một nhóm khách (US-26): bắt đầu từ bây giờ, hết hạn sau
 * suggestion.validDays ngày, mỗi khách 1 lượt, không giới hạn tổng (chỉ khách được tặng dùng được).
 * Mã gợi ý là mã nhóm kèm ngày tặng, vd AT_RISK-3110.
 */
export function suggestedVoucherForm(segmentCode, suggestion) {
  const now = nowMinute();
  const pad = (n) => String(n).padStart(2, '0');
  return {
    code: `${segmentCode}-${pad(now.getDate())}${pad(now.getMonth() + 1)}`,
    discountType: suggestion.discountType,
    discountValue: text(suggestion.discountValue),
    maxDiscountAmount: text(suggestion.maxDiscountAmount),
    minOrderAmount: text(suggestion.minOrderAmount),
    validFrom: toInputTime(now),
    validUntil: toInputTime(new Date(now.getTime() + suggestion.validDays * DAY_MS)),
    usageLimitTotal: '',
    usageLimitPerAccount: '1',
  };
}

/** Ô trống gửi null; mã giảm cố định không có trần nên bỏ trần đi. */
export function voucherRequestOf(form) {
  const number = (value) => (value.trim() === '' ? null : Number(value));
  return {
    code: form.code.trim().toUpperCase(),
    discountType: form.discountType,
    discountValue: number(form.discountValue),
    maxDiscountAmount: form.discountType === 'PERCENT' ? number(form.maxDiscountAmount) : null,
    minOrderAmount: number(form.minOrderAmount),
    validFrom: form.validFrom,
    validUntil: form.validUntil,
    usageLimitTotal: number(form.usageLimitTotal),
    usageLimitPerAccount: number(form.usageLimitPerAccount),
  };
}
