import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadSeenProducts, saveSeenProducts } from '../../src/services/recommendationSeen';

describe('recommendationSeen', () => {
  afterEach(() => vi.restoreAllMocks());

  it('lưu rồi đọc lại theo từng tài khoản', () => {
    expect(loadSeenProducts(7)).toBeNull();
    saveSeenProducts(7, [3, 1]);
    expect(loadSeenProducts(7)).toEqual([3, 1]);
    expect(loadSeenProducts(8)).toBeNull();
  });

  it('dữ liệu hỏng hoặc localStorage bị chặn thì coi như chưa thấy gì', () => {
    localStorage.setItem('goi-y-da-thay:7', '{"a":1}');
    expect(loadSeenProducts(7)).toBeNull();
    localStorage.setItem('goi-y-da-thay:7', 'không phải JSON');
    expect(loadSeenProducts(7)).toBeNull();

    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bị chặn');
    });
    expect(() => saveSeenProducts(7, [1])).not.toThrow();
  });
});
