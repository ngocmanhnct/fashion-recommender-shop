import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/context/authContext';
import CartProvider from '../../src/context/CartProvider';
import { useCart } from '../../src/hooks/useCart';
import * as cartApi from '../../src/services/cartApi';
import { readGuestCart, writeGuestCart } from '../../src/services/guestCart';
import { authValue, users } from '../renderWithContext';

vi.mock('../../src/services/cartApi');

/** Giỏ backend trả về cho các dòng [{ variantId, quantity }]. */
function cartOf(lines, notices = []) {
  return {
    items: lines.map((l) => ({ ...l, unitPrice: 100000 })),
    notices,
    totalQuantity: lines.reduce((sum, l) => sum + l.quantity, 0),
    subtotal: lines.reduce((sum, l) => sum + l.quantity * 100000, 0),
  };
}

function renderCart({ user = null, loading = false } = {}) {
  const auth = authValue({ user, loading });
  return renderHook(() => useCart(), {
    wrapper: ({ children }) => (
      <AuthContext.Provider value={auth}>
        <CartProvider>{children}</CartProvider>
      </AuthContext.Provider>
    ),
  });
}

describe('CartProvider', () => {
  beforeEach(() => {
    vi.mocked(cartApi.previewCart).mockImplementation((lines) => Promise.resolve(cartOf(lines)));
    vi.mocked(cartApi.getCart).mockResolvedValue(cartOf([{ variantId: 5, quantity: 1 }]));
  });

  it('đang kiểm tra đăng nhập thì chưa có chế độ, chưa tải', () => {
    const { result } = renderCart({ loading: true });
    expect(result.current).toMatchObject({ mode: null, cart: null, loading: false, count: 0 });
  });

  describe('khách chưa đăng nhập', () => {
    it('giỏ trình duyệt trống thì không gọi API', async () => {
      const { result } = renderCart();
      await waitFor(() => expect(result.current.loading).toBe(false));
      expect(result.current.mode).toBe('guest');
      expect(result.current.cart.items).toEqual([]);
      expect(cartApi.previewCart).not.toHaveBeenCalled();
    });

    it('lấy giá, tồn kho của giỏ trình duyệt và lưu lại đúng các dòng backend trả về', async () => {
      writeGuestCart([
        { variantId: 1, quantity: 2 },
        { variantId: 2, quantity: 1 },
      ]);
      vi.mocked(cartApi.previewCart).mockResolvedValue(
        cartOf([{ variantId: 1, quantity: 2 }], [{ variantId: 2, type: 'REMOVED' }])
      );
      const { result } = renderCart();
      await waitFor(() => expect(result.current.count).toBe(2));
      expect(result.current.notices).toEqual([{ variantId: 2, type: 'REMOVED' }]);
      expect(readGuestCart()).toEqual([{ variantId: 1, quantity: 2 }]);
    });

    it('thêm, sửa, xóa dòng ghi vào trình duyệt rồi lấy giá mới', async () => {
      const { result } = renderCart();
      await waitFor(() => expect(result.current.loading).toBe(false));

      await act(() => result.current.addItem(3, 2));
      expect(readGuestCart()).toEqual([{ variantId: 3, quantity: 2 }]);
      expect(result.current.count).toBe(2);

      await act(() => result.current.updateQuantity(3, 5));
      expect(result.current.count).toBe(5);

      await act(() => result.current.removeItem(3));
      expect(readGuestCart()).toEqual([]);
      expect(result.current.count).toBe(0);
    });

    it('giỏ đã đủ 50 dòng thì thêm dòng mới trả lỗi, không gọi API', async () => {
      writeGuestCart(Array.from({ length: 50 }, (_, i) => ({ variantId: i + 1, quantity: 1 })));
      const { result } = renderCart();
      await waitFor(() => expect(result.current.count).toBe(50));
      vi.mocked(cartApi.previewCart).mockClear();
      await expect(result.current.addItem(99, 1)).rejects.toThrow('tối đa 50');
      expect(cartApi.previewCart).not.toHaveBeenCalled();
    });
  });

  describe('khách hàng', () => {
    it('đọc giỏ của tài khoản', async () => {
      const { result } = renderCart({ user: users.CUSTOMER });
      expect(result.current.loading).toBe(true);
      await waitFor(() => expect(result.current.count).toBe(1));
      expect(result.current.mode).toBe('customer');
      expect(cartApi.mergeCart).not.toHaveBeenCalled();
    });

    it('vừa đăng nhập thì gộp giỏ trình duyệt vào tài khoản rồi xóa nó', async () => {
      writeGuestCart([{ variantId: 9, quantity: 3 }]);
      vi.mocked(cartApi.mergeCart).mockResolvedValue(cartOf([{ variantId: 9, quantity: 3 }]));
      const { result } = renderCart({ user: users.CUSTOMER });
      await waitFor(() => expect(result.current.count).toBe(3));
      expect(cartApi.mergeCart).toHaveBeenCalledWith([{ variantId: 9, quantity: 3 }]);
      expect(cartApi.getCart).not.toHaveBeenCalled();
      expect(readGuestCart()).toEqual([]);
    });

    it('lần tải đang chạy được dùng chung, không gộp 2 lần', async () => {
      writeGuestCart([{ variantId: 9, quantity: 3 }]);
      vi.mocked(cartApi.mergeCart).mockResolvedValue(cartOf([{ variantId: 9, quantity: 3 }]));
      const { result } = renderCart({ user: users.CUSTOMER });
      await act(() => result.current.refresh());
      expect(cartApi.mergeCart).toHaveBeenCalledTimes(1);
    });

    it('thao tác gọi API giỏ của tài khoản và gom thông báo cho tới khi đóng', async () => {
      const { result } = renderCart({ user: users.CUSTOMER });
      await waitFor(() => expect(result.current.count).toBe(1));
      const reduced = { variantId: 5, type: 'REDUCED' };
      vi.mocked(cartApi.addCartItem).mockResolvedValue(
        cartOf([{ variantId: 5, quantity: 2 }], [reduced])
      );
      vi.mocked(cartApi.updateCartItem).mockResolvedValue(
        cartOf([{ variantId: 5, quantity: 4 }], [reduced])
      );
      vi.mocked(cartApi.removeCartItem).mockResolvedValue(cartOf([]));

      await act(() => result.current.addItem(5, 1));
      expect(cartApi.addCartItem).toHaveBeenCalledWith(5, 1);
      await act(() => result.current.updateQuantity(5, 4));
      expect(cartApi.updateCartItem).toHaveBeenCalledWith(5, 4);
      expect(result.current.notices).toEqual([reduced]);

      act(() => result.current.dismissNotices());
      expect(result.current.notices).toEqual([]);

      await act(() => result.current.removeItem(5));
      expect(cartApi.removeCartItem).toHaveBeenCalledWith(5);
      expect(result.current.count).toBe(0);
    });

    it('tải lỗi thì giữ lỗi để thử lại; thao tác lỗi chỉ trả về nơi gọi', async () => {
      vi.mocked(cartApi.getCart).mockRejectedValueOnce(new Error('mất mạng'));
      const { result } = renderCart({ user: users.CUSTOMER });
      await waitFor(() => expect(result.current.error?.message).toBe('mất mạng'));
      expect(result.current.cart).toBeNull();

      await act(() => result.current.refresh());
      expect(result.current.error).toBeNull();
      expect(result.current.count).toBe(1);

      vi.mocked(cartApi.addCartItem).mockRejectedValue(new Error('hết hàng'));
      await act(async () => {
        await expect(result.current.addItem(5, 1)).rejects.toThrow('hết hàng');
      });
      expect(result.current.error).toBeNull();
      expect(result.current.count).toBe(1);
    });
  });

  it('quản trị viên không có giỏ hàng', async () => {
    const { result } = renderCart({ user: users.ADMIN });
    await waitFor(() => expect(result.current.mode).toBe('none'));
    expect(result.current).toMatchObject({ cart: null, loading: false, count: 0 });
    await expect(result.current.addItem(1, 1)).rejects.toThrow('Không có giỏ hàng');
  });
});
