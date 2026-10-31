import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import FormField from '../components/FormField';
import { resetPassword } from '../services/authApi';
import { getErrorMessage, getFieldErrors } from '../services/errorMessage';

/**
 * Trang /reset-password?token=... (chức năng 21, BR-48), mở từ liên kết trong email: đặt mật khẩu
 * mới rồi chuyển tới trang đăng nhập. Liên kết sai, đã dùng hoặc hết hạn thì mời xin liên kết mới.
 */
function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  const [form, setForm] = useState({ newPassword: '', confirmPassword: '' });
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  if (!token) {
    return (
      <div className="auth-card">
        <h1>Đặt lại mật khẩu</h1>
        <p className="alert alert-error">
          Liên kết thiếu mã đặt lại mật khẩu. Hãy mở đúng liên kết trong email, hoặc xin liên kết
          mới.
        </p>
        <Link to="/forgot-password" className="btn btn-primary btn-block">
          Xin liên kết mới
        </Link>
      </div>
    );
  }

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    if (form.newPassword !== form.confirmPassword) {
      setFieldErrors({ confirmPassword: 'Mật khẩu nhập lại không khớp' });
      return;
    }
    setFieldErrors({});
    setSubmitting(true);
    try {
      await resetPassword(token, form.newPassword);
      navigate('/login', {
        replace: true,
        state: { notice: 'Đã đặt mật khẩu mới. Hãy đăng nhập bằng mật khẩu mới.' },
      });
    } catch (err) {
      setError(getErrorMessage(err, 'Không đặt lại được mật khẩu'));
      setFieldErrors(getFieldErrors(err));
      setSubmitting(false);
    }
  };

  // Lỗi không gắn với ô nào là lỗi của liên kết (không hợp lệ, đã dùng, hết hạn).
  const linkError = error && Object.keys(fieldErrors).length === 0;

  return (
    <div className="auth-card">
      <h1>Đặt lại mật khẩu</h1>
      <form onSubmit={handleSubmit} noValidate>
        <FormField
          label="Mật khẩu mới (8 ký tự trở lên, có chữ và số)"
          error={fieldErrors.newPassword}
        >
          <input
            type="password"
            value={form.newPassword}
            onChange={update('newPassword')}
            autoComplete="new-password"
          />
        </FormField>
        <FormField label="Nhập lại mật khẩu mới" error={fieldErrors.confirmPassword}>
          <input
            type="password"
            value={form.confirmPassword}
            onChange={update('confirmPassword')}
            autoComplete="new-password"
          />
        </FormField>
        {error && <p className="alert alert-error">{error}</p>}
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Đang lưu...' : 'Đặt mật khẩu mới'}
        </button>
      </form>
      {linkError && (
        <p className="muted">
          <Link to="/forgot-password">Xin liên kết mới</Link>
        </p>
      )}
    </div>
  );
}

export default ResetPasswordPage;
