import { useState } from 'react';
import { useSearchParams } from 'react-router';
import Pagination from '../../components/Pagination';
import VoucherForm from '../../components/VoucherForm';
import { useApiData } from '../../hooks/useApiData';
import { getErrorMessage } from '../../services/errorMessage';
import { formatPrice } from '../../services/format';
import { formatDateTime } from '../../services/orderLabels';
import {
  createVoucher,
  searchVouchers,
  setVoucherActive,
  updateVoucher,
} from '../../services/voucherAdminApi';
import { emptyVoucherForm, voucherFormOf } from '../../services/voucherForm';
import {
  describeDiscount,
  describeUsage,
  VOUCHER_STATUS_LABELS,
} from '../../services/voucherLabels';

const STATUS_CLASS = {
  ACTIVE: 'status status-delivered',
  SCHEDULED: 'status',
};

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

      {editing === 'new' && (
        <VoucherForm
          key="new"
          title="Tạo mã mới"
          submitLabel="Tạo mã"
          initialForm={emptyVoucherForm()}
          onSubmit={async (request) => handleSaved(await createVoucher(request), 'Đã tạo')}
          onCancel={() => setEditing(null)}
        />
      )}
      {editing !== null && editing !== 'new' && (
        <VoucherForm
          key={editing.id}
          title={`Sửa mã ${editing.code}`}
          submitLabel="Lưu thay đổi"
          initialForm={voucherFormOf(editing)}
          lockCode={editing.usedCount > 0}
          onSubmit={async (request) =>
            handleSaved(await updateVoucher(editing.id, request), 'Đã lưu')
          }
          onCancel={() => setEditing(null)}
        >
          {editing.recipientsOnly && (
            <p className="muted small">Mã tặng riêng: chỉ khách được tặng dùng được.</p>
          )}
        </VoucherForm>
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
                      {v.recipientsOnly && <span className="badge">Tặng riêng</span>}
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
