import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { vi } from 'vitest';
import { AuthContext } from '../src/context/authContext';
import { CartContext } from '../src/context/cartContext';

/** Giá trị giả của AuthContext: mặc định chưa đăng nhập. */
export function authValue(overrides = {}) {
  return {
    user: null,
    loading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    ...overrides,
  };
}

/** Giá trị giả của CartContext: mặc định giỏ trống của khách chưa đăng nhập. */
export function cartValue(overrides = {}) {
  return {
    mode: 'guest',
    cart: { items: [], notices: [], totalQuantity: 0, subtotal: 0 },
    loading: false,
    error: null,
    notices: [],
    count: 0,
    refresh: vi.fn(() => Promise.resolve(null)),
    addItem: vi.fn(() => Promise.resolve(null)),
    updateQuantity: vi.fn(() => Promise.resolve(null)),
    removeItem: vi.fn(() => Promise.resolve(null)),
    dismissNotices: vi.fn(),
    ...overrides,
  };
}

const CUSTOMER = { id: 7, email: 'khach@example.com', fullName: 'Khách Thử', role: 'CUSTOMER' };
const ADMIN = { id: 1, email: 'admin@example.com', fullName: 'Quản Trị', role: 'ADMIN' };
export const users = { CUSTOMER, ADMIN };

// Trang bị chuyển tới (Navigate) hiện đường dẫn và state để test kiểm tra.
function LocationProbe() {
  const location = useLocation();
  return (
    <p data-testid="location">
      {location.pathname}
      {location.search}
      {location.state ? ` ${JSON.stringify(location.state)}` : ''}
    </p>
  );
}

/**
 * Vẽ ui ở đường dẫn path (có thể kèm ?truy vấn) với AuthContext, CartContext giả và router trong
 * bộ nhớ. route là mẫu đường dẫn của ui (vd '/products/:id'); mọi đường dẫn khác hiện
 * LocationProbe.
 */
export function renderWithContext(
  ui,
  { auth = authValue(), cart = cartValue(), path = '/', route = path.split('?')[0], state } = {}
) {
  const [pathname, search = ''] = path.split('?');
  const entry = { pathname, search: search ? `?${search}` : '', state };
  const tree = (element) => (
    <AuthContext.Provider value={auth}>
      <CartContext.Provider value={cart}>
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path={route} element={element} />
            <Route path="*" element={<LocationProbe />} />
          </Routes>
        </MemoryRouter>
      </CartContext.Provider>
    </AuthContext.Provider>
  );
  const result = render(tree(ui));
  // rerender giữ nguyên Context và router, chỉ thay ui.
  return { ...result, rerender: (next) => result.rerender(tree(next)), auth, cart };
}
