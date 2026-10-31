import { useState } from 'react';
import { Link } from 'react-router';
import FormField from '../components/FormField';
import { requestPasswordReset } from '../services/authApi';
import { getErrorMessage, getFieldErrors } from '../services/errorMessage';

/**
 * Trang /forgot-password (chức năng 21, BR-48): nhập email để nhận liên kết đặt lại mật khẩu. Câu
 * báo sau khi gửi giống nhau dù email có tài khoản hay không, để không dò được email đã đăng ký.
 */
function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sentTo, setSentTo] = useState(null);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      await requestPasswordReset(email.trim());
      setSentTo(email.trim());
    } catch (err) {
      setError(getErrorMessage(err, 'Không gửi được yêu cầu'));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (sentTo) {
    return (
      <div className="auth-card">
        <h1>Kiểm tra hộp thư</h1>
        <p role="status">
          Nếu <strong>{sentTo}</strong> có tài khoản, liên kết đặt lại mật khẩu đã được gửi tới hộp
          thư đó. Liên kết dùng được 1 lần và hết hạn sau 30 phút.
        </p>
        <p className="muted small">
          Không thấy email thì xem thư mục Spam, hoặc gửi lại sau 1 phút.
        </p>
        <button
          type="button"
          className="btn btn-ghost btn-block"
          onClick={() => {
            setEmail(sentTo);
            setSentTo(null);
          }}
        >
          Gửi lại
        </button>
        <p className="muted">
          <Link to="/login">Quay lại đăng nhập</Link>
        </p>
      </div>
    );
  }

  return (
    <div className="auth-card">
      <h1>Quên mật khẩu</h1>
      <p className="muted">Nhập email đã đăng ký, chúng tôi sẽ gửi liên kết để đặt mật khẩu mới.</p>
      <form onSubmit={handleSubmit} noValidate>
        <FormField label="Email" error={fieldErrors.email}>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
          />
        </FormField>
        {error && <p className="alert alert-error">{error}</p>}
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Đang gửi...' : 'Gửi liên kết'}
        </button>
      </form>
      <p className="muted">
        Nhớ ra mật khẩu? <Link to="/login">Đăng nhập</Link>
      </p>
    </div>
  );
}

export default ForgotPasswordPage;
