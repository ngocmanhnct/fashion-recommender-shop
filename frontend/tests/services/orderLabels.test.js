import { describe, expect, it } from 'vitest';
import { ORDER_STATUS_LABELS, formatDateTime } from '../../src/services/orderLabels';

describe('orderLabels', () => {
  it('định dạng ngày giờ kiểu Việt Nam, thiếu thì gạch ngang', () => {
    expect(formatDateTime('2026-10-21T14:05:00')).toBe('21/10/2026 14:05');
    expect(formatDateTime(null)).toBe('—');
  });

  it('có nhãn cho cả 5 trạng thái đơn', () => {
    expect(Object.keys(ORDER_STATUS_LABELS)).toEqual([
      'PENDING',
      'PROCESSING',
      'SHIPPING',
      'DELIVERED',
      'CANCELLED',
    ]);
  });
});
