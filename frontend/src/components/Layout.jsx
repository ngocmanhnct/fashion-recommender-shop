import { useEffect, useId, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { useCart } from '../hooks/useCart';
import { usePressRipple } from '../hooks/usePressRipple';
import { getCategories } from '../services/catalogApi';
import { groupCategories } from '../services/categoryGroups';
import { formatPrice } from '../services/format';
import { productsPath } from '../services/productQuery';
import CategoryMenu from './CategoryMenu';
import Icon from './Icon';
import SearchBox from './SearchBox';

/** Đơn từ mức này được miễn phí vận chuyển (BR-35, OrderService.shippingFeeFor). */
const FREE_SHIPPING_FROM = 500000;

/**
 * Khung chung của mọi trang: dải thông tin trên cùng, thanh chính (logo, ô tìm, lối tắt tài
 * khoản, giỏ hàng), hàng menu danh mục, nội dung trang (hiện dần mỗi lần chuyển trang) và chân
 * trang. Màn hình hẹp thì menu thu vào ngăn kéo mở bằng nút ☰.
 */
function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { pathname } = location;
  // Ngăn kéo đang mở gắn với lượt chuyển trang lúc mở: sang trang khác là tự đóng.
  const [drawerFor, setDrawerFor] = useState(null);
  const drawerOpen = drawerFor === location.key;
  const drawerId = useId();
  usePressRipple();

  // BrowserRouter không tự cuộn khi chuyển trang (ScrollRestoration cần data router): sang trang
  // khác thì cuộn lên đầu, nếu không mở sản phẩm ở cuối danh sách sẽ thấy ngay phần cuối trang.
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  const handleLogout = () => {
    setDrawerFor(null);
    logout();
    navigate('/');
  };

  // Trang quản trị tự làm hiệu ứng cho phần nội dung, để menu bên trái đứng yên khi đổi mục.
  const transitionKey = pathname.startsWith('/admin') ? '/admin' : pathname;

  return (
    <div className="app">
      <a href="#noi-dung" className="skip-link">
        Bỏ qua menu
      </a>
      <header className="site-header">
        <div className="utility-bar">
          <div className="container utility-inner">
            <p>Miễn phí giao hàng cho đơn từ {formatPrice(FREE_SHIPPING_FROM)}</p>
            {user && (
              <div className="utility-account">
                <span className="topbar-greeting">Xin chào, {user.fullName}</span>
                <Link to="/profile">Hồ sơ</Link>
                {user.role === 'CUSTOMER' && <Link to="/addresses">Sổ địa chỉ</Link>}
                <button type="button" className="btn-link" onClick={handleLogout}>
                  Đăng xuất
                </button>
              </div>
            )}
          </div>
        </div>
        <div className="container masthead">
          <button
            type="button"
            className="icon-btn drawer-toggle"
            aria-expanded={drawerOpen}
            aria-controls={drawerId}
            onClick={() => setDrawerFor(drawerOpen ? null : location.key)}
          >
            <Icon name={drawerOpen ? 'close' : 'menu'} size={22} />
            <span className="visually-hidden">{drawerOpen ? 'Đóng menu' : 'Mở menu'}</span>
          </button>
          <Link to="/" className="brand">
            <span className="brand-mark" aria-hidden="true">
              F
            </span>
            Fashion Shop
          </Link>
          <SearchBox />
          <HeaderActions user={user} />
        </div>
        <CategoryMenu />
      </header>

      <MobileDrawer
        id={drawerId}
        open={drawerOpen}
        user={user}
        onClose={() => setDrawerFor(null)}
        onLogout={handleLogout}
      />

      <main id="noi-dung" className="page">
        <div key={transitionKey} className="page-transition">
          <Outlet />
        </div>
      </main>

      <SiteFooter />
    </div>
  );
}

/** Lối tắt bên phải thanh chính: theo vai trò, kèm giỏ hàng (có số lượng) và nút đăng nhập. */
function HeaderActions({ user }) {
  const { mode: cartMode, count: cartCount } = useCart();
  return (
    <nav className="header-actions" aria-label="Tài khoản">
      {user?.role === 'ADMIN' && (
        <NavLink to="/admin" className="icon-link">
          <Icon name="user" />
          <span className="icon-link-label">Quản trị</span>
        </NavLink>
      )}
      {user?.role === 'CUSTOMER' && (
        <>
          <NavLink to="/offers" className="icon-link">
            <Icon name="ticket" />
            <span className="icon-link-label">Ưu đãi</span>
          </NavLink>
          <NavLink to="/wishlist" className="icon-link">
            <Icon name="heart" />
            <span className="icon-link-label">Yêu thích</span>
          </NavLink>
          <NavLink to="/orders" className="icon-link">
            <Icon name="receipt" />
            <span className="icon-link-label">Đơn hàng</span>
          </NavLink>
        </>
      )}
      {cartMode !== 'none' && (
        <NavLink to="/cart" className="icon-link cart-link">
          <Icon name="bag" />
          <span className="icon-link-label">Giỏ hàng</span>
          {cartCount > 0 && (
            // key đổi theo số lượng: thêm hàng là badge được vẽ lại và "nảy" lên 1 lần.
            <span key={cartCount} className="cart-badge" aria-label={`${cartCount} sản phẩm`}>
              {cartCount > 99 ? '99+' : cartCount}
            </span>
          )}
        </NavLink>
      )}
      {!user && (
        <>
          <NavLink to="/login" className="icon-link guest-link">
            <Icon name="user" />
            <span className="icon-link-label">Đăng nhập</span>
          </NavLink>
          <NavLink to="/register" className="btn btn-primary guest-link">
            Đăng ký
          </NavLink>
        </>
      )}
    </nav>
  );
}

/**
 * Ngăn kéo menu cho màn hình hẹp: các nhóm danh mục (mở rộng từng nhóm), lối tắt tài khoản. Khi
 * đóng vẫn nằm trong trang để trượt ra/vào được, nhưng bị ẩn khỏi bàn phím và trình đọc màn hình
 * (inert); màn hình rộng thì ẩn hẳn.
 */
function MobileDrawer({ id, open, user, onClose, onLogout }) {
  const { data } = useApiData('all', getCategories);
  const groups = groupCategories(data);
  return (
    <div className={open ? 'drawer is-open' : 'drawer'}>
      <button type="button" className="drawer-backdrop" tabIndex={-1} onClick={onClose}>
        <span className="visually-hidden">Đóng menu</span>
      </button>
      <div id={id} className="drawer-panel" inert={!open}>
        <p className="drawer-title">Danh mục</p>
        <ul className="drawer-links">
          <li>
            <Link to="/products">Tất cả sản phẩm</Link>
          </li>
          {groups.map((group) => (
            <li key={group.key}>
              <details>
                <summary>{group.name}</summary>
                <ul>
                  {group.categories.map((category) => (
                    <li key={category.id}>
                      <Link to={productsPath({ categoryId: String(category.id) })}>
                        {category.name}
                      </Link>
                    </li>
                  ))}
                </ul>
              </details>
            </li>
          ))}
          <li>
            <Link to="/survey">Khảo sát sở thích</Link>
          </li>
        </ul>
        <DrawerAccount user={user} onLogout={onLogout} />
      </div>
    </div>
  );
}

// Chữ trong ngăn kéo cố ý khác dải trên cùng ("Xin chào, ..."): ngăn kéo luôn nằm trong trang,
// trùng chữ thì test E2E tìm theo chữ sẽ thấy 2 chỗ.
function DrawerAccount({ user, onLogout }) {
  if (!user) {
    return (
      <div className="drawer-account">
        <Link to="/login" className="btn btn-ghost">
          Đăng nhập
        </Link>
        <Link to="/register" className="btn btn-primary">
          Đăng ký
        </Link>
      </div>
    );
  }
  return (
    <div className="drawer-account">
      <p className="drawer-title">Tài khoản của {user.fullName}</p>
      <ul className="drawer-links">
        <li>
          <Link to="/profile">Hồ sơ cá nhân</Link>
        </li>
        {user.role === 'ADMIN' && (
          <li>
            <Link to="/admin">Quản trị</Link>
          </li>
        )}
        {user.role === 'CUSTOMER' && (
          <>
            <li>
              <Link to="/orders">Đơn hàng</Link>
            </li>
            <li>
              <Link to="/offers">Ưu đãi của bạn</Link>
            </li>
            <li>
              <Link to="/wishlist">Yêu thích</Link>
            </li>
            <li>
              <Link to="/addresses">Sổ địa chỉ</Link>
            </li>
          </>
        )}
      </ul>
      <button type="button" className="btn btn-ghost" onClick={onLogout}>
        Đăng xuất
      </button>
    </div>
  );
}

/**
 * Chân trang. Tên liên kết ở đây cố ý khác các liên kết trên thanh trên cùng (Đăng nhập, Đơn
 * hàng, Giỏ hàng...) để test E2E tìm liên kết theo tên không bị trùng.
 */
function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="container footer-grid">
        <div className="footer-brand">
          <p className="brand">Fashion Shop</p>
          <p>Giày, túi và phụ kiện, kèm gợi ý sản phẩm theo đúng sở thích của bạn.</p>
        </div>
        <div>
          <h2 className="footer-title">Mua sắm</h2>
          <ul>
            <li>
              <Link to="/products">Xem mọi sản phẩm</Link>
            </li>
            <li>
              <Link to="/survey">Làm khảo sát sở thích</Link>
            </li>
          </ul>
        </div>
        <div>
          <h2 className="footer-title">Giao hàng và thanh toán</h2>
          <ul>
            <li>Miễn phí giao hàng từ {formatPrice(FREE_SHIPPING_FROM)}</li>
            <li>Trả tiền khi nhận hàng hoặc qua VNPay</li>
          </ul>
        </div>
        <div>
          <h2 className="footer-title">Dự án</h2>
          <ul>
            <li>Tiểu luận chuyên ngành, HCMUTE 2026</li>
            <li>
              <Link to="/health">Kiểm tra kết nối backend</Link>
            </li>
          </ul>
        </div>
      </div>
    </footer>
  );
}

export default Layout;
