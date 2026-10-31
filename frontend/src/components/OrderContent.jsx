import { Link } from 'react-router';
import { formatPrice } from '../services/format';

/**
 * Phần thân chi tiết đơn hàng: người nhận, sản phẩm và giá như lúc đặt (BR-15, BR-29), tổng tiền.
 * Dùng chung cho trang của khách và trang quản trị; productHref(item) cho biết link của mỗi dòng,
 * itemAction(item) (không bắt buộc) thêm 1 thao tác dưới mỗi dòng, vd "Đánh giá".
 */
function OrderContent({ order, productHref, itemAction }) {
  return (
    <>
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
                <Link to={productHref(item)}>{item.productName}</Link>
                {item.variantLabel && <p className="muted small">Phân loại: {item.variantLabel}</p>}
                {itemAction?.(item)}
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
    </>
  );
}

export default OrderContent;
