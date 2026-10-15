import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../hooks/useAuth';

/**
 * Chặn trang quản trị ở phía giao diện: chưa đăng nhập thì đưa về /login (nhớ trang đang muốn
 * vào), đăng nhập rồi nhưng không phải ADMIN thì về trang chủ. Đây chỉ là tiện cho người dùng;
 * bảo vệ thật nằm ở backend (/api/admin/** chỉ nhận token ADMIN).
 */
function RequireAdmin({ children }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <p className="muted">Đang tải...</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (user.role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }
  return children;
}

export default RequireAdmin;
