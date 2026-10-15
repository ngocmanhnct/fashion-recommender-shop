import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { useAuth } from '../hooks/useAuth';

/** Khung chung của mọi trang: thanh trên cùng (logo, menu, tài khoản) và nội dung trang. */
function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

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
        <nav className="topbar-nav">
          {user?.role === 'ADMIN' && <NavLink to="/admin">Quản trị</NavLink>}
          {user ? (
            <>
              <span className="muted">Xin chào, {user.fullName}</span>
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
