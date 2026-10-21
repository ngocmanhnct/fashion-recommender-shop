import { useEffect } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../hooks/useAuth';
import { useCart } from '../hooks/useCart';
import SearchBox from './SearchBox';

/**
 * Khung chung của mọi trang: thanh trên cùng (logo, ô tìm sản phẩm, menu, tài khoản) và nội
 * dung trang.
 */
function Layout() {
  const { user, logout } = useAuth();
  const { mode: cartMode, count: cartCount } = useCart();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  // BrowserRouter không tự cuộn khi chuyển trang (ScrollRestoration cần data router): sang trang
  // khác thì cuộn lên đầu, nếu không mở sản phẩm ở cuối danh sách sẽ thấy ngay phần cuối trang.
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="brand">
          Fashion Shop
        </Link>
        <SearchBox />
        <nav className="topbar-nav">
          <NavLink to="/products">Sản phẩm</NavLink>
          {user?.role === 'ADMIN' && <NavLink to="/admin">Quản trị</NavLink>}
          {user?.role === 'CUSTOMER' && <NavLink to="/orders">Đơn hàng</NavLink>}
          {cartMode !== 'none' && (
            <NavLink to="/cart" className="cart-link">
              Giỏ hàng
              {cartCount > 0 && (
                <span className="cart-badge" aria-label={`${cartCount} sản phẩm`}>
                  {cartCount > 99 ? '99+' : cartCount}
                </span>
              )}
            </NavLink>
          )}
          {user ? (
            <>
              <span className="muted topbar-greeting">Xin chào, {user.fullName}</span>
              <button type="button" className="btn btn-ghost" onClick={handleLogout}>
                Đăng xuất
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">Đăng nhập</NavLink>
              <NavLink to="/register" className="btn btn-primary">
                Đăng ký
              </NavLink>
            </>
          )}
        </nav>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </div>
  );
}

export default Layout;
