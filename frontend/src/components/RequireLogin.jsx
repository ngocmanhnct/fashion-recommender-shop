import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../hooks/useAuth';

/**
 * Chặn trang cần đăng nhập nhưng dành cho mọi vai trò (vd hồ sơ cá nhân): chưa đăng nhập thì đưa
 * tới /login và quay lại đúng trang này sau khi đăng nhập. Bảo vệ thật nằm ở backend.
 */
function RequireLogin({ children }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <p className="muted">Đang tải...</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}

export default RequireLogin;
