import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import OrderContent from '../components/OrderContent';
import PaymentInfo from '../components/PaymentInfo';
import { useApiData } from '../hooks/useApiData';
import { getErrorMessage } from '../services/errorMessage';
import { cancelOrder, getOrder } from '../services/orderApi';
import { goToVnPay } from '../services/vnpayRedirect';
import {
  formatDateTime,
  ORDER_STATUS_LABELS,
  PAYMENT_METHOD_LABELS,
} from '../services/orderLabels';

const ORDER_ID_PATTERN = /^\d{1,18}$/;

/**
 * Trang chi tiết đơn hàng /orders/:id (US-11, US-20): sản phẩm và giá như lúc mua (BR-15), người
 * nhận và địa chỉ lúc đặt (BR-29), nút tự hủy khi đơn còn Chờ xử lý hoặc Đang xử lý (BR-13).
 */
function OrderDetailPage() {
  const { id } = useParams();
  if (!ORDER_ID_PATTERN.test(id)) return <OrderNotFound />;
  return <OrderDetail key={id} id={id} />;
}

function OrderDetail({ id }) {
  const location = useLocation();
  const { data, error, loading, reload } = useApiData(id, getOrder);
  // Đơn sau khi hủy (backend trả về đơn mới), dùng thay dữ liệu đã tải.
  const [updated, setUpdated] = useState(null);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState('');
  const [paying, setPaying] = useState(false);
  const navigate = useNavigate();
  const order = updated ?? data;

  if (loading) return <p className="muted">Đang tải đơn hàng...</p>;
  if (error) {
    if (error.response?.status === 404) return <OrderNotFound />;
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không tải được đơn hàng')}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }

  const handlePay = async () => {
    setPaying(true);
    await goToVnPay(order.id, navigate);
    setPaying(false);
  };

  const handleCancel = async () => {
    if (!window.confirm(`Hủy đơn #${order.id}?`)) return;
    setCancelError('');
    setCancelling(true);
    try {
      setUpdated(await cancelOrder(order.id));
    } catch (err) {
      setCancelError(getErrorMessage(err, 'Không hủy được đơn hàng'));
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="narrow-page">
      <p className="small">
        <Link to="/orders">‹ Đơn hàng của tôi</Link>
      </p>
      {location.state?.placed && !updated && (
        <p className="alert alert-success" role="status">
          Đặt hàng thành công! Mã đơn hàng của bạn là #{order.id}.
        </p>
      )}
      <div className="order-card-head">
        <h1>Đơn #{order.id}</h1>
        <span className={`status status-${order.status.toLowerCase()}`}>
          {ORDER_STATUS_LABELS[order.status]}
        </span>
      </div>
      <p className="muted small">
        Đặt lúc {formatDateTime(order.createdAt)} · {PAYMENT_METHOD_LABELS[order.paymentMethod]}
      </p>

      <PaymentInfo order={order}>
        {location.state?.paymentError && !paying && (
          <p className="alert alert-error small" role="alert">
            {location.state.paymentError}
          </p>
        )}
        {order.paymentDeadline && (
          <button type="button" className="btn btn-primary" disabled={paying} onClick={handlePay}>
            {paying ? 'Đang mở VNPay...' : 'Thanh toán qua VNPay'}
          </button>
        )}
      </PaymentInfo>

      <OrderContent order={order} productHref={(item) => `/products/${item.productId}`} />

      {cancelError && (
        <p className="alert alert-error" role="alert">
          {cancelError}
        </p>
      )}
      {order.cancellable ? (
        <button
          type="button"
          className="btn btn-ghost"
          disabled={cancelling}
          onClick={handleCancel}
        >
          {cancelling ? 'Đang hủy...' : 'Hủy đơn hàng'}
        </button>
      ) : (
        (order.status === 'SHIPPING' || order.status === 'DELIVERED') && (
          <p className="muted small">
            Đơn hàng đã rời kho nên không thể tự hủy. Hãy liên hệ cửa hàng nếu cần hỗ trợ.
          </p>
        )
      )}
    </div>
  );
}

function OrderNotFound() {
  return (
    <div className="empty-state">
      <h1>Không tìm thấy đơn hàng</h1>
      <Link to="/orders" className="btn btn-ghost">
        Xem đơn hàng của tôi
      </Link>
    </div>
  );
}

export default OrderDetailPage;
