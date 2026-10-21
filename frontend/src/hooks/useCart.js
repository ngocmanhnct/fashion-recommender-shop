import { useContext } from 'react';
import { CartContext } from '../context/cartContext';

/**
 * Trả về giỏ hàng hiện tại và các thao tác, xem CartProvider. mode: 'guest' (chưa đăng nhập,
 * giỏ lưu ở trình duyệt), 'customer' (giỏ lưu ở backend), 'none' (quản trị viên, không mua hàng),
 * null (đang kiểm tra đăng nhập).
 */
export function useCart() {
  const value = useContext(CartContext);
  if (!value) {
    throw new Error('useCart phải dùng bên trong <CartProvider>');
  }
  return value;
}
