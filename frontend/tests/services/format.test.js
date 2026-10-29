import { describe, expect, it } from 'vitest';
import {
  formatNumber,
  formatPrice,
  formatPriceRange,
  formatRating,
} from '../../src/services/format';

// Intl chèn khoảng trắng không ngắt (U+00A0) trước ký hiệu ₫: so sánh sau khi đổi về dấu cách.
const plain = (text) => text.replace(/\s/g, ' ');

describe('format', () => {
  it('định dạng giá VNĐ, thiếu giá thì hiện gạch ngang', () => {
    expect(plain(formatPrice(150000))).toBe('150.000 ₫');
    expect(formatPrice(null)).toBe('—');
  });

  it('khoảng giá gộp lại khi giá thấp nhất bằng cao nhất', () => {
    expect(plain(formatPriceRange(100000, 100000))).toBe('100.000 ₫');
    expect(plain(formatPriceRange(100000, 150000))).toBe('100.000 ₫ – 150.000 ₫');
    expect(formatPriceRange(null, null)).toBe('—');
  });

  it('số đếm và điểm đánh giá theo kiểu Việt Nam', () => {
    expect(formatNumber(1050)).toBe('1.050');
    expect(formatRating(4.5)).toBe('4,5');
    expect(formatRating(4)).toBe('4,0');
  });
});
