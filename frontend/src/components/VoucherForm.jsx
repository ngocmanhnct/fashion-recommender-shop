import { cloneElement, useState } from 'react';
import { getErrorMessage, getFieldErrors } from '../services/errorMessage';
import { voucherRequestOf } from '../services/voucherForm';

/**
 * Biểu mẫu điều kiện của một mã giảm giá, dùng chung cho trang quản trị mã (tạo, sửa) và chiến
 * dịch tặng mã theo nhóm khách (US-26).
 *
 * - initialForm: giá trị ban đầu các ô (services/voucherForm.js).
 * - lockCode: mã đã có người dùng thì không đổi được chuỗi mã.
 * - onSubmit(request): gửi lên API, trả về Promise; lỗi validate của backend (kể cả dạng
 *   "voucher.code" khi mã nằm trong yêu cầu lớn hơn) hiện ngay dưới ô.
 * - children: phần giới thiệu hiện dưới tiêu đề (vd số khách được tặng).
 */
function VoucherForm({
  title,
  submitLabel,
  initialForm,
  lockCode = false,
  onSubmit,
  onCancel,
  children,
}) {
  const [form, setForm] = useState(initialForm);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const percent = form.discountType === 'PERCENT';
  const locked = lockCode;

  const set = (name) => (event) => setForm({ ...form, [name]: event.target.value });

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError('');
    setFieldErrors({});
    try {
      await onSubmit(voucherRequestOf(form));
    } catch (err) {
      const errors = Object.entries(getFieldErrors(err)).map(([name, message]) => [
        name.replace(/^voucher\./, ''),
        message,
      ]);
      setFieldErrors(Object.fromEntries(errors));
      setError(getErrorMessage(err, 'Không lưu được mã'));
      setSaving(false);
    }
  };

  const field = (name, label, input, note) => {
    const id = `voucher-${name}`;
    return (
      <div className="field">
        <label className="field-label" htmlFor={id}>
          {label}
        </label>
        {cloneElement(input, { id, 'aria-describedby': note ? `${id}-note` : undefined })}
        {note && (
          <span id={`${id}-note`} className="muted small">
            {note}
          </span>
        )}
        {fieldErrors[name] && <span className="field-error">{fieldErrors[name]}</span>}
      </div>
    );
  };

  return (
    <form className="admin-panel voucher-editor" onSubmit={handleSubmit}>
      <h2>{title}</h2>
      {children}
      <div className="field-row">
        {field(
          'code',
          'Mã',
          <input
            value={form.code}
            onChange={set('code')}
            maxLength={50}
            required
            disabled={locked}
            placeholder="VD: CHAOBAN10"
            className="voucher-code-input"
          />,
          locked
            ? 'Mã đã có người dùng nên không đổi được chuỗi mã.'
            : 'Chữ không dấu, số, "-" hoặc "_", 3 đến 50 ký tự.'
        )}
        {field(
          'discountType',
          'Kiểu giảm',
          <select value={form.discountType} onChange={set('discountType')}>
            <option value="PERCENT">Giảm theo phần trăm</option>
            <option value="FIXED">Giảm số tiền cố định</option>
          </select>
        )}
      </div>
      <div className="field-row">
        {field(
          'discountValue',
          percent ? 'Phần trăm giảm' : 'Số tiền giảm (đ)',
          <input
            type="number"
            min={1}
            max={percent ? 100 : undefined}
            value={form.discountValue}
            onChange={set('discountValue')}
            required
          />
        )}
        {percent &&
          field(
            'maxDiscountAmount',
            'Giảm tối đa (đ)',
            <input
              type="number"
              min={1}
              value={form.maxDiscountAmount}
              onChange={set('maxDiscountAmount')}
            />,
            'Để trống nếu không giới hạn.'
          )}
        {field(
          'minOrderAmount',
          'Đơn tối thiểu (đ)',
          <input
            type="number"
            min={0}
            value={form.minOrderAmount}
            onChange={set('minOrderAmount')}
          />,
          'Tính trên tiền hàng, chưa gồm phí vận chuyển.'
        )}
      </div>
      <div className="field-row">
        {field(
          'validFrom',
          'Áp dụng từ',
          <input
            type="datetime-local"
            value={form.validFrom}
            onChange={set('validFrom')}
            required
          />
        )}
        {field(
          'validUntil',
          'Hết hạn lúc',
          <input
            type="datetime-local"
            value={form.validUntil}
            onChange={set('validUntil')}
            required
          />
        )}
      </div>
      <div className="field-row">
        {field(
          'usageLimitTotal',
          'Tổng số lượt',
          <input
            type="number"
            min={1}
            value={form.usageLimitTotal}
            onChange={set('usageLimitTotal')}
          />,
          'Để trống nếu không giới hạn.'
        )}
        {field(
          'usageLimitPerAccount',
          'Số lượt mỗi khách',
          <input
            type="number"
            min={1}
            value={form.usageLimitPerAccount}
            onChange={set('usageLimitPerAccount')}
          />,
          'Để trống nếu không giới hạn.'
        )}
      </div>
      {error && (
        <p className="alert alert-error" role="alert">
          {error}
        </p>
      )}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Đang lưu...' : submitLabel}
        </button>
        <button type="button" className="btn btn-ghost" onClick={onCancel}>
          Hủy
        </button>
      </div>
    </form>
  );
}

export default VoucherForm;
