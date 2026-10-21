import { useState } from 'react';
import { Link, useParams } from 'react-router';
import OrderContent from '../../components/OrderContent';
import { useApiData } from '../../hooks/useApiData';
import { getErrorMessage } from '../../services/errorMessage';
import { changeOrderStatus, getAdminOrder } from '../../services/orderAdminApi';
import {
  formatDateTime,
  ORDER_STATUS_LABELS,
  PAYMENT_METHOD_LABELS,
} from '../../services/orderLabels';

const ORDER_ID_PATTERN = /^\d{1,18}$/;

/**
 * Trang quản trị /admin/orders/:id (US-17): chi tiết đơn, chuyển sang trạng thái kế tiếp (BR-12)
 * hoặc hủy đơn thay khách khi đơn chưa Đã giao (BR-13), có hoàn tồn kho (BR-16).
 */
function OrderAdminDetailPage() {
  const { id } = useParams();
  if (!ORDER_ID_PATTERN.test(id)) return <AdminOrderNotFound />;
  return <AdminOrderDetail key={id} id={id} />;
}

function AdminOrderDetail({ id }) {
  const { data, error, loading, reload } = useApiData(id, getAdminOrder);
  // Đơn sau khi đổi trạng thái (backend trả về bản mới), dùng thay dữ liệu đã tải.
  const [updated, setUpdated] = useState(null);
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState('');
  const detail = updated ?? data;

  if (loading) return <p className="muted">Đang tải đơn hàng...</p>;
  if (error) {
    if (error.response?.status === 404) return <AdminOrderNotFound />;
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không tải được đơn hàng')}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }

  const { order } = detail;

  const handleChange = async (status) => {
    const question =
      status === 'CANCELLED'
        ? `Hủy đơn #${order.id}? Tồn kho của các sản phẩm trong đơn sẽ được hoàn lại.`
        : `Chuyển đơn #${order.id} sang "${ORDER_STATUS_LABELS[status]}"?`;
    if (!window.confirm(question)) return;
    setActionError('');
    setSaving(true);
    try {
      setUpdated(await changeOrderStatus(order.id, status));
    } catch (err) {
      setActionError(getErrorMessage(err, 'Không đổi được trạng thái đơn hàng'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="narrow-page">
      <p className="small">
        <Link to="/admin/orders">‹ Danh sách đơn hàng</Link>
      </p>
      <div className="order-card-head">
        <h1>Đơn #{order.id}</h1>
        <span className={`status status-${order.status.toLowerCase()}`}>
          {ORDER_STATUS_LABELS[order.status]}
        </span>
      </div>
      <p className="muted small">
        Đặt lúc {formatDateTime(order.createdAt)} · {PAYMENT_METHOD_LABELS[order.paymentMethod]} ·
        Cập nhật {formatDateTime(order.updatedAt)}
      </p>

      <section className="checkout-section">
        <h2>Khách hàng</h2>
        <p>
          <strong>{detail.customerName}</strong> · {detail.customerEmail}
        </p>
      </section>

      <OrderContent order={order} productHref={(item) => `/admin/products/${item.productId}`} />

      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}
      <div className="order-actions">
        {detail.nextStatus && (
          <button
            type="button"
            className="btn btn-primary"
            disabled={saving}
            onClick={() => handleChange(detail.nextStatus)}
          >
            Chuyển sang “{ORDER_STATUS_LABELS[detail.nextStatus]}”
          </button>
        )}
        {detail.adminCancellable && (
          <button
            type="button"
            className="btn btn-danger"
            disabled={saving}
            onClick={() => handleChange('CANCELLED')}
          >
            Hủy đơn
          </button>
        )}
        {!detail.nextStatus && !detail.adminCancellable && (
          <p className="muted small">Đơn đã kết thúc, không đổi trạng thái được nữa.</p>
        )}
      </div>
    </div>
  );
}

function AdminOrderNotFound() {
  return (
    <div className="empty-state">
      <h1>Không tìm thấy đơn hàng</h1>
      <Link to="/admin/orders" className="btn btn-ghost">
        Danh sách đơn hàng
      </Link>
    </div>
  );
}

export default OrderAdminDetailPage;
