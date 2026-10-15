import { useState } from 'react';
import { Link, Navigate } from 'react-router';
import FormField from '../components/FormField';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage, getFieldErrors } from '../services/errorMessage';

const EMPTY_FORM = {
  fullName: '',
  email: '',
  password: '',
  confirmPassword: '',
  gender: '',
  birthdate: '',
};

function RegisterPage() {
  const { user, register } = useAuth();
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  // Đăng ký xong là đã đăng nhập: chuyển về trang chủ.
  if (user) {
    return <Navigate to="/" replace />;
  }

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    if (form.password !== form.confirmPassword) {
      setFieldErrors({ confirmPassword: 'Mật khẩu nhập lại không khớp' });
      return;
    }
    setFieldErrors({});
    setSubmitting(true);
    try {
      await register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        gender: form.gender || null,
        birthdate: form.birthdate || null,
      });
    } catch (err) {
      setError(getErrorMessage(err, 'Đăng ký không thành công'));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-card">
      <h1>Đăng ký</h1>
      <form onSubmit={handleSubmit} noValidate>
        <FormField label="Họ và tên" error={fieldErrors.fullName}>
          <input value={form.fullName} onChange={update('fullName')} autoComplete="name" />
        </FormField>
        <FormField label="Email" error={fieldErrors.email}>
          <input type="email" value={form.email} onChange={update('email')} autoComplete="email" />
        </FormField>
        <FormField label="Mật khẩu (8 ký tự trở lên, có chữ và số)" error={fieldErrors.password}>
          <input
            type="password"
            value={form.password}
            onChange={update('password')}
            autoComplete="new-password"
          />
        </FormField>
        <FormField label="Nhập lại mật khẩu" error={fieldErrors.confirmPassword}>
          <input
            type="password"
            value={form.confirmPassword}
            onChange={update('confirmPassword')}
            autoComplete="new-password"
          />
        </FormField>
        <div className="field-row">
          <FormField label="Giới tính (không bắt buộc)" error={fieldErrors.gender}>
            <select value={form.gender} onChange={update('gender')}>
              <option value="">Không chọn</option>
              <option value="MALE">Nam</option>
              <option value="FEMALE">Nữ</option>
              <option value="OTHER">Khác</option>
            </select>
          </FormField>
          <FormField label="Ngày sinh (không bắt buộc)" error={fieldErrors.birthdate}>
            <input type="date" value={form.birthdate} onChange={update('birthdate')} />
          </FormField>
        </div>
        {error && <p className="alert alert-error">{error}</p>}
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Đang tạo tài khoản...' : 'Đăng ký'}
        </button>
      </form>
      <p className="muted">
        Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
      </p>
    </div>
  );
}

export default RegisterPage;
