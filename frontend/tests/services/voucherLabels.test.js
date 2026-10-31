import { describe, expect, it } from 'vitest';
import { describeDiscount, describeUsage } from '../../src/services/voucherLabels';

// formatPrice dùng dấu cách không ngắt trước "₫".
const plain = (text) => text.replace(/\s/g, ' ');

describe('voucherLabels', () => {
  it('mô tả mức giảm theo phần trăm (có hoặc không có trần) và số tiền cố định', () => {
    expect(
      plain(
        describeDiscount({ discountType: 'PERCENT', discountValue: 10, maxDiscountAmount: 20000 })
      )
    ).toBe('Giảm 10%, tối đa 20.000 ₫');
    expect(describeDiscount({ discountType: 'PERCENT', discountValue: 5 })).toBe('Giảm 5%');
    expect(plain(describeDiscount({ discountType: 'FIXED', discountValue: 30000 }))).toBe(
      'Giảm 30.000 ₫'
    );
  });

  it('mô tả số lượt đã dùng, giới hạn tổng và mỗi khách', () => {
    expect(describeUsage({ usedCount: 3, usageLimitTotal: 100, usageLimitPerAccount: 1 })).toBe(
      '3 / 100 lượt, mỗi khách 1'
    );
    expect(describeUsage({ usedCount: 0, usageLimitTotal: null, usageLimitPerAccount: null })).toBe(
      '0 lượt (không giới hạn)'
    );
  });
});
