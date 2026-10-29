import { describe, expect, it, vi } from 'vitest';
import {
  MAX_LINES,
  MAX_QUANTITY,
  addGuestLine,
  clearGuestCart,
  readGuestCart,
  removeGuestLine,
  saveCheckedCart,
  setGuestLine,
} from '../../src/services/guestCart';

const KEY = 'fashionshop.guestCart';

describe('guestCart', () => {
  it('giỏ rỗng khi chưa lưu gì hoặc dữ liệu hỏng', () => {
    expect(readGuestCart()).toEqual([]);
    localStorage.setItem(KEY, '{không phải JSON');
    expect(readGuestCart()).toEqual([]);
    localStorage.setItem(KEY, '{"variantId":1}');
    expect(readGuestCart()).toEqual([]);
  });

  it('bỏ dòng sai định dạng và trường thừa khi đọc', () => {
    localStorage.setItem(
      KEY,
      JSON.stringify([
        { variantId: 1, quantity: 2, price: 999 },
        { variantId: 0, quantity: 1 },
        { variantId: 2, quantity: 100 },
        { variantId: 3, quantity: 1.5 },
        null,
        'x',
      ])
    );
    expect(readGuestCart()).toEqual([{ variantId: 1, quantity: 2 }]);
  });

  it('cộng dồn số lượng khi thêm biến thể đã có, tối đa 99', () => {
    addGuestLine(5, 2);
    addGuestLine(5, 3);
    expect(readGuestCart()).toEqual([{ variantId: 5, quantity: 5 }]);
    addGuestLine(5, 200);
    expect(readGuestCart()).toEqual([{ variantId: 5, quantity: MAX_QUANTITY }]);
  });

  it('báo lỗi 409 khi đã đủ 50 dòng', () => {
    for (let id = 1; id <= MAX_LINES; id += 1) addGuestLine(id, 1);
    expect(() => addGuestLine(999, 1)).toThrow(/tối đa 50/);
    try {
      addGuestLine(999, 1);
    } catch (error) {
      expect(error.response.status).toBe(409);
    }
    // Biến thể đã có vẫn cộng dồn được.
    addGuestLine(1, 1);
    expect(readGuestCart()[0]).toEqual({ variantId: 1, quantity: 2 });
  });

  it('đổi số lượng, xóa dòng, xóa cả giỏ', () => {
    addGuestLine(1, 1);
    addGuestLine(2, 1);
    setGuestLine(2, 7);
    expect(readGuestCart()).toEqual([
      { variantId: 1, quantity: 1 },
      { variantId: 2, quantity: 7 },
    ]);
    removeGuestLine(1);
    expect(readGuestCart()).toEqual([{ variantId: 2, quantity: 7 }]);
    clearGuestCart();
    expect(localStorage.getItem(KEY)).toBeNull();
  });

  it('lưu lại đúng các dòng backend trả về', () => {
    addGuestLine(1, 5);
    saveCheckedCart({ items: [{ variantId: 1, quantity: 3, name: 'Giày' }] });
    expect(readGuestCart()).toEqual([{ variantId: 1, quantity: 3 }]);
  });

  it('localStorage bị chặn thì coi như giỏ rỗng, không ném lỗi', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(readGuestCart()).toEqual([]);
    expect(() => addGuestLine(1, 1)).not.toThrow();
  });
});
