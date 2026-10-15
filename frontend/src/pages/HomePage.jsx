import { Link } from 'react-router';
import { useAuth } from '../hooks/useAuth';

// Trang chủ tạm: danh sách sản phẩm và gợi ý cho khách làm ở các tuần sau.
function HomePage() {
  const { user } = useAuth();
  return (
    <div className="hero">
      <h1>Fashion Shop</h1>
      <p className="muted">Website bán hàng tích hợp học máy trong gợi ý mua hàng.</p>
      {user ? (
        <p>
          Bạn đang đăng nhập với email <strong>{user.email}</strong>.
          {user.role === 'ADMIN' && (
            <>
              {' '}
              Vào <Link to="/admin">trang quản trị</Link>.
            </>
          )}
        </p>
      ) : (
        <p>
          <Link to="/login">Đăng nhập</Link> hoặc <Link to="/register">tạo tài khoản</Link> để bắt
          đầu.
        </p>
      )}
      <p className="muted small">
        <Link to="/health">Kiểm tra kết nối backend</Link>
      </p>
    </div>
  );
}

export default HomePage;
