import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../hooks/useAuth';

/**
 * Chặn trang chỉ dành cho khách hàng (vd thanh toán, US-08): chưa đăng nhập thì đưa tới /login và
 * quay lại đúng trang này sau khi đăng nhập hoặc đăng ký; quản trị viên thì về trang quản trị. Bảo
 * vệ thật nằm ở backend.
 */
function RequireCustomer({ children }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <p className="muted">Đang tải...</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (user.role !== 'CUSTOMER') {
    return <Navigate to="/admin" replace />;
  }
  return children;
}

export default RequireCustomer;
