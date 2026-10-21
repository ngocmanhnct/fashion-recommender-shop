import { useState } from 'react';
import { getErrorMessage, getFieldErrors } from '../services/errorMessage';
import FormField from './FormField';

const EMPTY = { recipientName: '', phone: '', addressDetail: '', isDefault: false };

/**
 * Form thêm hoặc sửa một địa chỉ giao hàng (US-09). onSubmit(address) trả về Promise; lỗi của
 * backend (thiếu trường, số điện thoại sai, sổ đã đủ 10 địa chỉ) hiện ngay trong form.
 */
function AddressForm({ initial, submitLabel, onSubmit, onCancel }) {
  const [values, setValues] = useState(initial ?? EMPTY);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const change = (field) => (event) => {
    const value = field === 'isDefault' ? event.target.checked : event.target.value;
    setValues((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      // Bỏ khoảng trắng, dấu chấm, gạch ngang trong số điện thoại (vd "090 123 4567").
      await onSubmit({ ...values, phone: values.phone.replace(/[\s.-]/g, '') });
    } catch (err) {
      setError(getErrorMessage(err, 'Không lưu được địa chỉ'));
      setFieldErrors(getFieldErrors(err));
      setSubmitting(false);
      return;
    }
    setSubmitting(false);
  };

  return (
    <form className="address-form" onSubmit={handleSubmit} noValidate>
      <FormField label="Tên người nhận" error={fieldErrors.recipientName}>
        <input
          value={values.recipientName}
          onChange={change('recipientName')}
          autoComplete="name"
          maxLength={255}
          required
        />
      </FormField>
      <FormField label="Số điện thoại" error={fieldErrors.phone}>
        <input
          type="tel"
          value={values.phone}
          onChange={change('phone')}
          autoComplete="tel"
          inputMode="tel"
          maxLength={20}
          required
        />
      </FormField>
      <FormField
        label="Địa chỉ (số nhà, đường, phường/xã, tỉnh/thành)"
        error={fieldErrors.addressDetail}
      >
        <textarea
          value={values.addressDetail}
          onChange={change('addressDetail')}
          autoComplete="street-address"
          maxLength={500}
          rows={2}
          required
        />
      </FormField>
      <label className="checkbox">
        <input type="checkbox" checked={values.isDefault} onChange={change('isDefault')} />
        Đặt làm địa chỉ mặc định
      </label>
      {error && (
        <p className="alert alert-error" role="alert">
          {error}
        </p>
      )}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={submitting}>
          {submitting ? 'Đang lưu...' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={submitting}>
            Hủy
          </button>
        )}
      </div>
    </form>
  );
}

export default AddressForm;
