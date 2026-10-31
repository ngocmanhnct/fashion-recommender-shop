import { useState } from 'react';
import { Link } from 'react-router';
import FormField from '../components/FormField';
import { useAuth } from '../hooks/useAuth';
import { changePassword, updateProfile } from '../services/authApi';
import { getErrorMessage, getFieldErrors } from '../services/errorMessage';

const EMPTY_PASSWORDS = { currentPassword: '', newPassword: '', confirmPassword: '' };

/**
 * Trang /profile (chức năng 21): sửa họ tên, giới tính, ngày sinh và đổi mật khẩu. Dùng cho cả
 * khách hàng lẫn quản trị viên; email không đổi được.
 */
function ProfilePage() {
  const { user } = useAuth();
  return (
    <div className="profile-page">
      <div className="page-header">
        <h1>Hồ sơ cá nhân</h1>
      </div>
      <div className="profile-forms">
        <ProfileForm user={user} />
        <PasswordForm />
      </div>
    </div>
  );
}

function ProfileForm({ user }) {
  const { updateUser } = useAuth();
  const [form, setForm] = useState({
    fullName: user.fullName,
    gender: user.gender ?? '',
    birthdate: user.birthdate ?? '',
  });
  const [status, setStatus] = useState({ error: '', fieldErrors: {}, saved: false });
  const [saving, setSaving] = useState(false);

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleSubmit = async (event) => {
    event.preventDefault();
    setStatus({ error: '', fieldErrors: {}, saved: false });
    setSaving(true);
    try {
      const account = await updateProfile({
        fullName: form.fullName.trim(),
        gender: form.gender || null,
        birthdate: form.birthdate || null,
      });
      updateUser(account);
      setStatus({ error: '', fieldErrors: {}, saved: true });
    } catch (err) {
      setStatus({
        error: getErrorMessage(err, 'Không lưu được hồ sơ'),
        fieldErrors: getFieldErrors(err),
        saved: false,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="checkout-section" aria-labelledby="ho-so-thong-tin">
      <h2 id="ho-so-thong-tin">Thông tin cá nhân</h2>
      <form onSubmit={handleSubmit} noValidate>
        <p className="profile-email">
          <span className="field-label">Email đăng nhập</span>
          <strong>{user.email}</strong>
        </p>
        <FormField label="Họ và tên" error={status.fieldErrors.fullName}>
          <input value={form.fullName} onChange={update('fullName')} autoComplete="name" />
        </FormField>
        <div className="field-row">
          <FormField label="Giới tính (không bắt buộc)" error={status.fieldErrors.gender}>
            <select value={form.gender} onChange={update('gender')}>
              <option value="">Không chọn</option>
              <option value="MALE">Nam</option>
              <option value="FEMALE">Nữ</option>
              <option value="OTHER">Khác</option>
            </select>
          </FormField>
          <FormField label="Ngày sinh (không bắt buộc)" error={status.fieldErrors.birthdate}>
            <input type="date" value={form.birthdate} onChange={update('birthdate')} />
          </FormField>
        </div>
        {status.error && <p className="alert alert-error">{status.error}</p>}
        {status.saved && (
          <p className="alert alert-success" role="status">
            Đã lưu hồ sơ
          </p>
        )}
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Đang lưu...' : 'Lưu hồ sơ'}
        </button>
      </form>
    </section>
  );
}

function PasswordForm() {
  const [form, setForm] = useState(EMPTY_PASSWORDS);
  const [status, setStatus] = useState({ error: '', fieldErrors: {}, saved: false });
  const [saving, setSaving] = useState(false);

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (form.newPassword !== form.confirmPassword) {
      setStatus({
        error: '',
        fieldErrors: { confirmPassword: 'Mật khẩu nhập lại không khớp' },
        saved: false,
      });
      return;
    }
    setStatus({ error: '', fieldErrors: {}, saved: false });
    setSaving(true);
    try {
      await changePassword(form.currentPassword, form.newPassword);
      setForm(EMPTY_PASSWORDS);
      setStatus({ error: '', fieldErrors: {}, saved: true });
    } catch (err) {
      setStatus({
        error: getErrorMessage(err, 'Không đổi được mật khẩu'),
        fieldErrors: getFieldErrors(err),
        saved: false,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="checkout-section" aria-labelledby="ho-so-mat-khau">
      <h2 id="ho-so-mat-khau">Đổi mật khẩu</h2>
      <form onSubmit={handleSubmit} noValidate>
        <FormField label="Mật khẩu hiện tại" error={status.fieldErrors.currentPassword}>
          <input
            type="password"
            value={form.currentPassword}
            onChange={update('currentPassword')}
            autoComplete="current-password"
          />
        </FormField>
        <FormField
          label="Mật khẩu mới (8 ký tự trở lên, có chữ và số)"
          error={status.fieldErrors.newPassword}
        >
          <input
            type="password"
            value={form.newPassword}
            onChange={update('newPassword')}
            autoComplete="new-password"
          />
        </FormField>
        <FormField label="Nhập lại mật khẩu mới" error={status.fieldErrors.confirmPassword}>
          <input
            type="password"
            value={form.confirmPassword}
            onChange={update('confirmPassword')}
            autoComplete="new-password"
          />
        </FormField>
        {status.error && <p className="alert alert-error">{status.error}</p>}
        {status.saved && (
          <p className="alert alert-success" role="status">
            Đã đổi mật khẩu. Lần đăng nhập sau dùng mật khẩu mới.
          </p>
        )}
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Đang đổi...' : 'Đổi mật khẩu'}
        </button>
        <p className="muted small">
          Quên mật khẩu hiện tại? <Link to="/forgot-password">Lấy lại qua email</Link>
        </p>
      </form>
    </section>
  );
}

export default ProfilePage;
