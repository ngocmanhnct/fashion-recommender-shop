import { cloneElement, useState } from 'react';
import { useSearchParams } from 'react-router';
import Pagination from '../../components/Pagination';
import { useApiData } from '../../hooks/useApiData';
import { getErrorMessage, getFieldErrors } from '../../services/errorMessage';
import { formatPrice } from '../../services/format';
import { formatDateTime } from '../../services/orderLabels';
import {
  createVoucher,
  searchVouchers,
  setVoucherActive,
  updateVoucher,
} from '../../services/voucherAdminApi';
import {
  describeDiscount,
  describeUsage,
  VOUCHER_STATUS_LABELS,
} from '../../services/voucherLabels';

const STATUS_CLASS = {
  ACTIVE: 'status status-delivered',
  SCHEDULED: 'status',
};

/** Giờ địa phương dạng "yyyy-MM-ddTHH:mm" cho ô datetime-local. */
function toInputTime(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}`;
}

function emptyForm() {
  const now = new Date();
  now.setSeconds(0, 0);
  const week = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
  return {
    code: '',
    discountType: 'PERCENT',
    discountValue: '',
    maxDiscountAmount: '',
    minOrderAmount: '',
    validFrom: toInputTime(now),
    validUntil: toInputTime(week),
    usageLimitTotal: '',
    usageLimitPerAccount: '1',
  };
}

function formOf(voucher) {
  const text = (value) => (value == null ? '' : String(value));
  return {
    code: voucher.code,
    discountType: voucher.discountType,
    discountValue: text(voucher.discountValue),
    maxDiscountAmount: text(voucher.maxDiscountAmount),
    minOrderAmount: text(voucher.minOrderAmount),
    validFrom: voucher.validFrom.slice(0, 16),
    validUntil: voucher.validUntil.slice(0, 16),
    usageLimitTotal: text(voucher.usageLimitTotal),
    usageLimitPerAccount: text(voucher.usageLimitPerAccount),
  };
}

/** Ô trống gửi null; mã giảm cố định không có trần nên bỏ trần đi. */
function requestOf(form) {
  const number = (value) => (value.trim() === '' ? null : Number(value));
  return {
    code: form.code.trim().toUpperCase(),
    discountType: form.discountType,
    discountValue: number(form.discountValue),
    maxDiscountAmount: form.discountType === 'PERCENT' ? number(form.maxDiscountAmount) : null,
    minOrderAmount: number(form.minOrderAmount),
    validFrom: form.validFrom,
    validUntil: form.validUntil,
    usageLimitTotal: number(form.usageLimitTotal),
    usageLimitPerAccount: number(form.usageLimitPerAccount),
  };
}

/** Biểu mẫu tạo hoặc sửa 1 mã. */
function VoucherForm({ voucher, onSaved, onCancel }) {
  const [form, setForm] = useState(() => (voucher ? formOf(voucher) : emptyForm()));
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const percent = form.discountType === 'PERCENT';
  const locked = voucher != null && voucher.usedCount > 0;

  const set = (name) => (event) => setForm({ ...form, [name]: event.target.value });

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError('');
    setFieldErrors({});
    try {
      const request = requestOf(form);
      const saved = voucher
        ? await updateVoucher(voucher.id, request)
        : await createVoucher(request);
      onSaved(saved, voucher ? 'Đã lưu' : 'Đã tạo');
    } catch (err) {
      setFieldErrors(getFieldErrors(err));
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
      <h2>{voucher ? `Sửa mã ${voucher.code}` : 'Tạo mã mới'}</h2>
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
          {saving ? 'Đang lưu...' : voucher ? 'Lưu thay đổi' : 'Tạo mã'}
        </button>
        <button type="button" className="btn btn-ghost" onClick={onCancel}>
          Hủy
        </button>
      </div>
    </form>
  );
}

/**
 * Trang quản trị /admin/vouchers (US-22): danh sách mã kèm mức giảm, thời hạn, số lượt đã dùng và
 * tình trạng; tạo, sửa, ngừng hoặc bật lại mã. Khách nhập mã ở trang thanh toán (US-21).
 */
function VoucherAdminPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('keyword') ?? '';
  const pageParam = Number.parseInt(searchParams.get('page') ?? '1', 10);
  const page = Number.isInteger(pageParam) && pageParam > 0 ? pageParam - 1 : 0;
  const { data, lastData, error, loading, reload } = useApiData(
    JSON.stringify({ keyword, page }),
    (key) => searchVouchers(JSON.parse(key))
  );
  const result = data ?? lastData;
  // null: không mở biểu mẫu; 'new': tạo mã; còn lại là mã đang sửa.
  const [editing, setEditing] = useState(null);
  const [message, setMessage] = useState('');
  const [actionError, setActionError] = useState('');

  const update = (next) => {
    const merged = { keyword, page, ...next };
    const params = {};
    if (merged.keyword) params.keyword = merged.keyword;
    if (merged.page > 0) params.page = String(merged.page + 1);
    setSearchParams(params);
  };

  const handleSearch = (event) => {
    event.preventDefault();
    update({ keyword: new FormData(event.currentTarget).get('keyword').trim(), page: 0 });
  };

  const handleSaved = (saved, verb) => {
    setEditing(null);
    setActionError('');
    setMessage(`${verb} mã ${saved.code}`);
    reload();
  };

  const handleToggle = async (voucher) => {
    const active = !voucher.active;
    if (!active && !window.confirm(`Ngừng áp dụng mã ${voucher.code}? Đơn đã đặt không đổi.`)) {
      return;
    }
    setMessage('');
    setActionError('');
    try {
      await setVoucherActive(voucher.id, active);
      setMessage(active ? `Đã bật lại mã ${voucher.code}` : `Đã ngừng mã ${voucher.code}`);
      reload();
    } catch (err) {
      setActionError(getErrorMessage(err, 'Không đổi được tình trạng mã'));
    }
  };

  const openForm = (target) => {
    setMessage('');
    setActionError('');
    setEditing(target);
  };

  return (
    <div>
      <div className="page-header">
        <h1>Mã giảm giá</h1>
        {editing === null && (
          <button type="button" className="btn btn-primary" onClick={() => openForm('new')}>
            Tạo mã mới
          </button>
        )}
      </div>
      <p className="muted small">
        Thời hạn tính theo giờ Việt Nam. Khách nhập mã ở bước thanh toán; đơn bị hủy thì mã được trả
        lại lượt dùng.
      </p>

      {editing !== null && (
        <VoucherForm
          key={editing === 'new' ? 'new' : editing.id}
          voucher={editing === 'new' ? null : editing}
          onSaved={handleSaved}
          onCancel={() => setEditing(null)}
        />
      )}
      {message && (
        <p className="alert alert-success" role="status">
          {message}
        </p>
      )}
      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}

      <form className="inline-form" onSubmit={handleSearch}>
        <input key={keyword} name="keyword" placeholder="Tìm theo mã" defaultValue={keyword} />
        <button type="submit" className="btn btn-ghost">
          Tìm
        </button>
      </form>

      {error && (
        <div className="alert alert-error">
          <p>{getErrorMessage(error, 'Không tải được danh sách mã')}</p>
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Thử lại
          </button>
        </div>
      )}
      {!result && loading && <p className="muted">Đang tải...</p>}

      {result && (
        <>
          <p className="muted small">{result.totalElements} mã</p>
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Mã</th>
                  <th>Thời hạn</th>
                  <th>Lượt dùng</th>
                  <th>Tình trạng</th>
                  <th className="actions">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((v) => (
                  <tr key={v.id}>
                    <td>
                      <strong className="voucher-code">{v.code}</strong>
                      <div className="small">{describeDiscount(v)}</div>
                      {v.minOrderAmount && (
                        <div className="muted small">Đơn từ {formatPrice(v.minOrderAmount)}</div>
                      )}
                    </td>
                    <td className="small">
                      {formatDateTime(v.validFrom)}
                      <div className="muted">đến {formatDateTime(v.validUntil)}</div>
                    </td>
                    <td className="small">{describeUsage(v)}</td>
                    <td>
                      <span className={STATUS_CLASS[v.status] ?? 'status status-muted'}>
                        {VOUCHER_STATUS_LABELS[v.status] ?? v.status}
                      </span>
                    </td>
                    <td className="actions">
                      <button type="button" className="btn btn-ghost" onClick={() => openForm(v)}>
                        Sửa
                      </button>
                      <button
                        type="button"
                        className={v.active ? 'btn btn-danger' : 'btn btn-ghost'}
                        onClick={() => handleToggle(v)}
                      >
                        {v.active ? 'Ngừng' : 'Bật lại'}
                      </button>
                    </td>
                  </tr>
                ))}
                {result.content.length === 0 && (
                  <tr>
                    <td colSpan={5} className="muted">
                      {keyword ? 'Không có mã nào khớp.' : 'Chưa có mã giảm giá nào.'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            onChange={(next) => update({ page: next })}
          />
        </>
      )}
    </div>
  );
}

export default VoucherAdminPage;
