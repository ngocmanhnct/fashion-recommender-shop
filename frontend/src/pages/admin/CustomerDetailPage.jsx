import { useState } from 'react';
import { Link, useParams } from 'react-router';
import Pagination from '../../components/Pagination';
import SegmentBadge from '../../components/SegmentBadge';
import { useApiData } from '../../hooks/useApiData';
import { getCustomer, getCustomerOrders } from '../../services/customerAdminApi';
import { getErrorMessage } from '../../services/errorMessage';
import { formatNumber, formatPrice } from '../../services/format';
import {
  formatDateTime,
  ORDER_STATUS_LABELS,
  PAYMENT_METHOD_LABELS,
} from '../../services/orderLabels';

const ACCOUNT_ID_PATTERN = /^\d{1,18}$/;
const GENDER_LABELS = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };

/** Ngày sinh (LocalDate "2004-05-01") dạng "01/05/2004", không qua Date để khỏi lệch múi giờ. */
function formatBirthdate(value) {
  if (!value) return '—';
  const [year, month, day] = value.split('-');
  return `${day}/${month}/${year}`;
}

/**
 * Trang quản trị /admin/customers/:id (US-18, US-19): thông tin khách, thống kê đơn, nhóm hiện tại
 * kèm 3 chỉ số R, F, M mà mô hình dùng để phân nhóm, và lịch sử đơn hàng.
 */
function CustomerDetailPage() {
  const { id } = useParams();
  if (!ACCOUNT_ID_PATTERN.test(id)) return <CustomerNotFound />;
  return <CustomerDetail key={id} id={id} />;
}

function CustomerDetail({ id }) {
  const { data: customer, error, loading, reload } = useApiData(id, getCustomer);

  if (loading) return <p className="muted">Đang tải khách hàng...</p>;
  if (error) {
    if (error.response?.status === 404) return <CustomerNotFound />;
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không tải được thông tin khách hàng')}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="narrow-page">
      <p className="small">
        <Link to="/admin/customers">‹ Danh sách khách hàng</Link>
      </p>
      <div className="order-card-head">
        <h1>{customer.fullName}</h1>
        <SegmentBadge segment={customer.segment} />
      </div>
      <p className="muted small">
        {customer.email} · Đăng ký lúc {formatDateTime(customer.createdAt)}
      </p>

      <section className="checkout-section">
        <h2>Thông tin</h2>
        <dl className="info-grid">
          <dt>Giới tính</dt>
          <dd>{GENDER_LABELS[customer.gender] ?? '—'}</dd>
          <dt>Ngày sinh</dt>
          <dd>{formatBirthdate(customer.birthdate)}</dd>
          <dt>Số đơn đã đặt</dt>
          <dd>{formatNumber(customer.orderCount)}</dd>
          <dt>Số đơn đã giao</dt>
          <dd>{formatNumber(customer.deliveredOrderCount)}</dd>
          <dt>Tổng tiền đơn đã giao</dt>
          <dd>{formatPrice(customer.deliveredTotal)}</dd>
        </dl>
      </section>

      <SegmentSection segment={customer.segment} />

      <section className="checkout-section">
        <h2>Lịch sử đơn hàng</h2>
        <CustomerOrders id={id} />
      </section>
    </div>
  );
}

/** Nhóm hiện tại và 3 chỉ số R, F, M lúc được phân nhóm (BR-23, BR-24). */
function SegmentSection({ segment }) {
  if (!segment) {
    return (
      <section className="checkout-section">
        <h2>Phân nhóm</h2>
        <p className="muted">
          Khách chưa được phân nhóm: chưa có đơn nào ở trạng thái Đã giao, hoặc mô hình chưa chạy.
          Khách sẽ được xếp nhóm ngay sau khi có đơn Đã giao đầu tiên.
        </p>
      </section>
    );
  }
  return (
    <section className="checkout-section">
      <h2>Phân nhóm: {segment.name}</h2>
      {segment.description && <p>{segment.description}</p>}
      <div className="rfm-grid">
        <div className="rfm-cell">
          <span className="muted small">R · Lần mua gần nhất</span>
          <strong>{formatNumber(segment.recency)} ngày trước</strong>
        </div>
        <div className="rfm-cell">
          <span className="muted small">F · Số đơn đã giao</span>
          <strong>{formatNumber(segment.frequency)} đơn</strong>
        </div>
        <div className="rfm-cell">
          <span className="muted small">M · Tổng chi tiêu</span>
          <strong>{formatPrice(segment.monetary)}</strong>
        </div>
      </div>
      <p className="muted small">Xếp nhóm lúc {formatDateTime(segment.computedAt)}.</p>
    </section>
  );
}

/** Đơn hàng của khách, mới nhất trước, mỗi trang 10 đơn. */
function CustomerOrders({ id }) {
  const [page, setPage] = useState(0);
  const { data, lastData, error, loading, reload } = useApiData(`${id}:${page}`, () =>
    getCustomerOrders(id, page)
  );
  const result = data ?? lastData;

  if (error) {
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không tải được đơn hàng')}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }
  if (!result) return loading ? <p className="muted">Đang tải...</p> : null;
  if (result.totalElements === 0) return <p className="muted">Khách chưa đặt đơn nào.</p>;

  return (
    <>
      <table className="table">
        <thead>
          <tr>
            <th>Mã đơn</th>
            <th>Ngày đặt</th>
            <th>Sản phẩm</th>
            <th>Tổng tiền</th>
            <th>Trạng thái</th>
          </tr>
        </thead>
        <tbody>
          {result.content.map((o) => (
            <tr key={o.id}>
              <td>
                <Link to={`/admin/orders/${o.id}`}>#{o.id}</Link>
              </td>
              <td>{formatDateTime(o.createdAt)}</td>
              <td>
                {o.firstProductName}
                {o.itemCount > 1 && (
                  <span className="muted small"> và {o.itemCount - 1} sản phẩm khác</span>
                )}
              </td>
              <td>
                {formatPrice(o.total)}
                <div className="muted small">{PAYMENT_METHOD_LABELS[o.paymentMethod]}</div>
              </td>
              <td>
                <span className={`status status-${o.status.toLowerCase()}`}>
                  {ORDER_STATUS_LABELS[o.status]}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
    </>
  );
}

function CustomerNotFound() {
  return (
    <div className="empty-state">
      <h1>Không tìm thấy khách hàng</h1>
      <Link to="/admin/customers" className="btn btn-ghost">
        Danh sách khách hàng
      </Link>
    </div>
  );
}

export default CustomerDetailPage;
