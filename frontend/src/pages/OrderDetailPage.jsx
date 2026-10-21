import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { getErrorMessage } from '../services/errorMessage';
import { formatPrice } from '../services/format';
import { cancelOrder, getOrder } from '../services/orderApi';
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

      <section className="checkout-section">
        <h2>Người nhận</h2>
        <p>
          <strong>{order.recipientName}</strong> · {order.phone}
        </p>
        <p className="muted small">{order.addressDetail}</p>
      </section>

      <section className="checkout-section">
        <h2>Sản phẩm</h2>
        <ul className="order-lines">
          {order.items.map((item) => (
            <li key={item.variantId} className="order-line">
              <div>
                <Link to={`/products/${item.productId}`}>{item.productName}</Link>
                {item.variantLabel && <p className="muted small">Phân loại: {item.variantLabel}</p>}
              </div>
              <span className="muted small">
                {formatPrice(item.unitPrice)} × {item.quantity}
              </span>
              <strong>{formatPrice(item.lineTotal)}</strong>
            </li>
          ))}
        </ul>
        <p className="cart-summary-row">
          <span>Tổng tiền hàng</span>
          <span>{formatPrice(order.subtotal)}</span>
        </p>
        <p className="cart-summary-row">
          <span>Phí vận chuyển</span>
          <span>{order.shippingFee === 0 ? 'Miễn phí' : formatPrice(order.shippingFee)}</span>
        </p>
        {order.discount > 0 && (
          <p className="cart-summary-row">
            <span>Giảm giá</span>
            <span>−{formatPrice(order.discount)}</span>
          </p>
        )}
        <p className="cart-summary-row cart-summary-total">
          <span>Tổng thanh toán</span>
          <strong>{formatPrice(order.total)}</strong>
        </p>
      </section>

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
