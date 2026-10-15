import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import FormField from '../components/FormField';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage, getFieldErrors } from '../services/errorMessage';

function LoginPage() {
  const { user, login } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  // Đăng nhập xong (hoặc đã đăng nhập từ trước) thì chuyển trang ngay tại đây: về trang đang muốn
  // vào trước khi bị đưa tới /login, không có thì admin vào /admin, khách về trang chủ.
  if (user) {
    const defaultPage = user.role === 'ADMIN' ? '/admin' : '/';
    return <Navigate to={location.state?.from || defaultPage} replace />;
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      await login(email.trim(), password);
    } catch (err) {
      // 401 sai email/mật khẩu, 429 bị khóa tạm do sai nhiều lần: backend đã viết sẵn thông báo.
      setError(getErrorMessage(err, 'Đăng nhập không thành công'));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-card">
      <h1>Đăng nhập</h1>
      <form onSubmit={handleSubmit} noValidate>
        <FormField label="Email" error={fieldErrors.email}>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
        </FormField>
        <FormField label="Mật khẩu" error={fieldErrors.password}>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </FormField>
        {error && <p className="alert alert-error">{error}</p>}
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </button>
      </form>
      <p className="muted">
        Chưa có tài khoản? <Link to="/register">Đăng ký</Link>
      </p>
    </div>
  );
}

export default LoginPage;
