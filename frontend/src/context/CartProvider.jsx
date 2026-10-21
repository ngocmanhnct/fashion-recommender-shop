import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import * as cartApi from '../services/cartApi';
import {
  addGuestLine,
  clearGuestCart,
  readGuestCart,
  removeGuestLine,
  saveCheckedCart,
  setGuestLine,
} from '../services/guestCart';
import { CartContext } from './cartContext';

const EMPTY_CART = { items: [], notices: [], totalQuantity: 0, subtotal: 0 };

function modeOf(user, authLoading) {
  if (authLoading) return null;
  if (!user) return 'guest';
  return user.role === 'CUSTOMER' ? 'customer' : 'none';
}

/** Giỏ của trình duyệt theo giá, tồn kho hiện tại; lưu lại đúng các dòng backend trả về. */
function loadGuestCart() {
  const lines = readGuestCart();
  if (lines.length === 0) return Promise.resolve(EMPTY_CART);
  return cartApi.previewCart(lines).then((cart) => {
    saveCheckedCart(cart);
    return cart;
  });
}

/** Giỏ của tài khoản; còn giỏ của trình duyệt (vừa đăng nhập) thì gộp vào trước (BR-07). */
function loadCustomerCart() {
  const lines = readGuestCart();
  if (lines.length === 0) return cartApi.getCart();
  return cartApi.mergeCart(lines).then((cart) => {
    clearGuestCart();
    return cart;
  });
}

function loadCart(mode) {
  if (mode === 'guest') return loadGuestCart();
  if (mode === 'customer') return loadCustomerCart();
  return Promise.resolve(null);
}

// Gom thông báo (dòng bị loại, bị giảm số lượng) cho tới khi khách đóng ở trang giỏ hàng: thông
// báo có thể đến từ thao tác ở trang khác (vd thêm vào giỏ ở trang chi tiết).
function mergeNotices(current, incoming) {
  const key = (n) => `${n.variantId}:${n.type}`;
  const seen = new Set(current.map(key));
  return [...current, ...incoming.filter((n) => !seen.has(key(n)))];
}

/**
 * Giỏ hàng dùng chung cho mọi trang (US-06, US-07, BR-06, BR-07).
 *
 * - Chưa đăng nhập: giỏ lưu ở localStorage, giá và tồn kho lấy từ /api/cart/preview.
 * - Khách hàng: giỏ lưu ở backend. Ngay sau khi đăng nhập hoặc đăng ký, giỏ của trình duyệt được
 *   gộp vào giỏ của tài khoản rồi xóa khỏi trình duyệt.
 * - Quản trị viên không mua hàng: không có giỏ.
 *
 * Mỗi thao tác trả về Promise của giỏ hàng mới (lỗi thì reject, để nơi gọi hiện thông báo). Phản
 * hồi về muộn của request cũ bị bỏ qua, nên giỏ trên màn hình luôn là của thao tác sau cùng.
 */
function CartProvider({ children }) {
  const { user, loading: authLoading } = useAuth();
  const mode = modeOf(user, authLoading);
  const [state, setState] = useState({ mode: null, cart: null, error: null, notices: [] });
  const requestIdRef = useRef(0);

  // Chỉ gọi setState trong callback của Promise (luật react-hooks/set-state-in-effect). Lỗi của
  // lần tải giỏ được lưu để trang giỏ hàng hiện nút "Thử lại"; lỗi của thao tác (thêm, sửa, xóa)
  // chỉ trả về cho nơi gọi hiện ngay cạnh nút bấm, giỏ đang hiện giữ nguyên.
  const track = useCallback((forMode, promise, isLoad = false) => {
    const requestId = ++requestIdRef.current;
    return promise.then(
      (cart) => {
        if (requestId === requestIdRef.current) {
          setState((prev) => ({
            mode: forMode,
            cart,
            error: null,
            notices: mergeNotices(prev.mode === forMode ? prev.notices : [], cart?.notices ?? []),
          }));
        }
        return cart;
      },
      (error) => {
        if (isLoad && requestId === requestIdRef.current) {
          setState((prev) =>
            prev.mode === forMode
              ? { ...prev, error }
              : { mode: forMode, cart: null, error, notices: [] }
          );
        }
        throw error;
      }
    );
  }, []);

  // Một lần tải đang chạy được dùng chung: trang giỏ hàng và Provider cùng tải lúc mở trang (và
  // StrictMode chạy effect 2 lần) chỉ gửi 1 request, nên giỏ của trình duyệt không bị gộp 2 lần.
  const inflightRef = useRef(null);
  const loadShared = useCallback((forMode) => {
    if (inflightRef.current?.mode === forMode) return inflightRef.current.promise;
    const promise = loadCart(forMode);
    inflightRef.current = { mode: forMode, promise };
    const clear = () => {
      if (inflightRef.current?.promise === promise) inflightRef.current = null;
    };
    promise.then(clear, clear);
    return promise;
  }, []);

  useEffect(() => {
    if (mode === null) return;
    track(mode, loadShared(mode), true).catch(() => {
      /* Lỗi đã lưu trong state.error, trang giỏ hàng hiện nút "Thử lại". */
    });
  }, [mode, track, loadShared]);

  const refresh = useCallback(() => {
    if (mode === null) return Promise.resolve(null);
    return track(mode, loadShared(mode), true);
  }, [mode, track, loadShared]);

  const addItem = useCallback(
    (variantId, quantity) => {
      if (mode === 'customer') return track(mode, cartApi.addCartItem(variantId, quantity));
      if (mode === 'guest') {
        try {
          addGuestLine(variantId, quantity);
        } catch (error) {
          return Promise.reject(error);
        }
        return track(mode, loadGuestCart());
      }
      return Promise.reject(new Error('Không có giỏ hàng cho tài khoản này'));
    },
    [mode, track]
  );

  const updateQuantity = useCallback(
    (variantId, quantity) => {
      if (mode === 'customer') return track(mode, cartApi.updateCartItem(variantId, quantity));
      setGuestLine(variantId, quantity);
      return track(mode, loadGuestCart());
    },
    [mode, track]
  );

  const removeItem = useCallback(
    (variantId) => {
      if (mode === 'customer') return track(mode, cartApi.removeCartItem(variantId));
      removeGuestLine(variantId);
      return track(mode, loadGuestCart());
    },
    [mode, track]
  );

  const dismissNotices = useCallback(() => {
    setState((prev) => ({ ...prev, notices: [] }));
  }, []);

  const current = state.mode === mode;
  const cart = current ? state.cart : null;
  const value = useMemo(
    () => ({
      mode,
      cart,
      loading: mode !== null && mode !== 'none' && !current,
      error: current ? state.error : null,
      notices: current ? state.notices : [],
      count: cart?.totalQuantity ?? 0,
      refresh,
      addItem,
      updateQuantity,
      removeItem,
      dismissNotices,
    }),
    [
      mode,
      cart,
      current,
      state.error,
      state.notices,
      refresh,
      addItem,
      updateQuantity,
      removeItem,
      dismissNotices,
    ]
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export default CartProvider;
