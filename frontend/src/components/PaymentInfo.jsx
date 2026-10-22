import { formatPrice } from '../services/format';
import {
  formatDateTime,
  PAYMENT_STATUS_LABELS,
  REFUND_STATUS_LABELS,
} from '../services/orderLabels';

/**
 * Trạng thái thanh toán online (US-10, BR-34) và hoàn tiền (BR-28) của một đơn. Đơn COD không có
 * gì để hiện. admin: hiện lý do hoàn tiền thất bại (lỗi kỹ thuật) thay cho lời nhắn cho khách.
 * children là nút thao tác (thanh toán lại, xác nhận hoàn tiền thủ công).
 */
function PaymentInfo({ order, admin = false, children }) {
  if (order.paymentMethod !== 'ONLINE' && !order.refund) return null;
  return (
    <section className="checkout-section">
      <h2>Thanh toán VNPay</h2>
      <p>
        {PAYMENT_STATUS_LABELS[order.paymentStatus]}
        {order.paidAt && <span className="muted small"> · lúc {formatDateTime(order.paidAt)}</span>}
      </p>
      {order.paymentDeadline && (
        <p className="muted small">
          Hãy thanh toán trước {formatDateTime(order.paymentDeadline)}, quá hạn đơn sẽ tự hủy.
        </p>
      )}
      {order.refund && (
        <div className={`refund-box refund-${order.refund.status.toLowerCase()}`}>
          <p>
            <strong>{REFUND_STATUS_LABELS[order.refund.status]}</strong>:{' '}
            {formatPrice(order.refund.amount)} về tài khoản đã thanh toán
            {order.refund.completedAt && ` · ${formatDateTime(order.refund.completedAt)}`}
          </p>
          {admin && order.refund.failureReason && (
            <p className="muted small">{order.refund.failureReason}</p>
          )}
          {!admin && order.refund.status === 'FAILED' && (
            <p className="muted small">Cửa hàng đang xử lý thủ công và sẽ liên hệ với bạn.</p>
          )}
        </div>
      )}
      {children}
    </section>
  );
}

export default PaymentInfo;
