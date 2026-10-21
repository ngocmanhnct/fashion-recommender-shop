import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import ProductImage from '../components/ProductImage';
import QuantityStepper from '../components/QuantityStepper';
import { useCart } from '../hooks/useCart';
import { getErrorMessage } from '../services/errorMessage';
import { formatNumber, formatPrice } from '../services/format';
import { MAX_QUANTITY } from '../services/guestCart';

/**
 * Trang giỏ hàng (US-07, US-08): sửa số lượng, xóa từng dòng, tạm tính cập nhật ngay; thông báo
 * dòng bị loại vì ngừng bán, hết hàng hoặc bị giảm số lượng (BR-11). Bấm "Mua hàng" khi chưa đăng
 * nhập thì sang trang đăng nhập rồi quay lại bước thanh toán.
 */
function CartPage() {
  const { mode, cart, loading, error, notices, refresh, dismissNotices } = useCart();
  const navigate = useNavigate();

  // Mở trang là tải lại giỏ để có giá và tồn kho mới nhất.
  useEffect(() => {
    refresh().catch(() => {
      /* Lỗi hiện qua error của useCart. */
    });
  }, [refresh]);

  if (mode === 'none') {
    return <p className="muted">Tài khoản quản trị không dùng giỏ hàng.</p>;
  }

  const handleCheckout = () => {
    if (mode === 'guest') {
      navigate('/login', { state: { from: '/checkout' } });
    } else {
      navigate('/checkout');
    }
  };

  return (
    <div className="cart-page">
      <h1>Giỏ hàng</h1>
      {notices.length > 0 && (
        <div className="alert alert-warning" role="status">
          <ul className="notice-list">
            {notices.map((notice) => (
              <li key={`${notice.variantId}:${notice.type}`}>
                {notice.productName && (
                  <strong>
                    {notice.productName}
                    {notice.variantLabel && ` (${notice.variantLabel})`}:{' '}
                  </strong>
                )}
                {notice.message}
              </li>
            ))}
          </ul>
          <button type="button" className="btn-link small" onClick={dismissNotices}>
            Đã hiểu
          </button>
        </div>
      )}
      <CartContent
        cart={cart}
        loading={loading}
        error={error}
        onRetry={() => refresh().catch(() => {})}
        onCheckout={handleCheckout}
      />
    </div>
  );
}

function CartContent({ cart, loading, error, onRetry, onCheckout }) {
  if (!cart) {
    if (error) {
      return (
        <div className="alert alert-error">
          <p>{getErrorMessage(error, 'Không tải được giỏ hàng')}</p>
          <button type="button" className="btn btn-ghost" onClick={onRetry}>
            Thử lại
          </button>
        </div>
      );
    }
    return loading ? <p className="muted">Đang tải giỏ hàng...</p> : null;
  }
  if (cart.items.length === 0) {
    return (
      <div className="empty-state">
        <p>Giỏ hàng của bạn đang trống.</p>
        <Link to="/products" className="btn btn-primary">
          Xem sản phẩm
        </Link>
      </div>
    );
  }
  return (
    <div className="cart-layout">
      <ul className="cart-lines">
        {cart.items.map((item) => (
          <CartLine key={item.variantId} item={item} />
        ))}
      </ul>
      <aside className="cart-summary">
        <p className="cart-summary-row">
          <span>Tạm tính ({formatNumber(cart.totalQuantity)} sản phẩm)</span>
          <strong>{formatPrice(cart.subtotal)}</strong>
        </p>
        <p className="muted small">Phí vận chuyển và mã giảm giá được tính ở bước thanh toán.</p>
        <button type="button" className="btn btn-primary btn-block" onClick={onCheckout}>
          Mua hàng
        </button>
      </aside>
    </div>
  );
}

function CartLine({ item }) {
  const { updateQuantity, removeItem } = useCart();
  const [pending, setPending] = useState(false);
  const [lineError, setLineError] = useState('');

  const run = async (action) => {
    setPending(true);
    setLineError('');
    try {
      await action();
    } catch (err) {
      setLineError(getErrorMessage(err, 'Không cập nhật được giỏ hàng'));
    } finally {
      setPending(false);
    }
  };

  return (
    <li className="cart-line">
      <Link to={`/products/${item.productId}`} className="cart-line-image">
        <ProductImage src={item.thumbnailUrl} />
      </Link>
      <div className="cart-line-info">
        <Link to={`/products/${item.productId}`}>{item.productName}</Link>
        {item.variantLabel && <p className="muted small">Phân loại: {item.variantLabel}</p>}
        <p className="small">{formatPrice(item.unitPrice)}</p>
        <div className="cart-line-actions">
          <QuantityStepper
            value={item.quantity}
            max={Math.max(Math.min(item.stock, MAX_QUANTITY), item.quantity)}
            onChange={(quantity) => run(() => updateQuantity(item.variantId, quantity))}
            disabled={pending}
            label={`Số lượng ${item.productName}`}
          />
          <button
            type="button"
            className="btn-link small"
            disabled={pending}
            onClick={() => run(() => removeItem(item.variantId))}
          >
            Xóa
          </button>
        </div>
        {lineError && (
          <p className="alert alert-error small" role="alert">
            {lineError}
          </p>
        )}
      </div>
      <p className="cart-line-total">{formatPrice(item.lineTotal)}</p>
    </li>
  );
}

export default CartPage;
