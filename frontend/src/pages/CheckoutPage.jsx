import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import AddressForm from '../components/AddressForm';
import AddressText from '../components/AddressText';
import { useApiData } from '../hooks/useApiData';
import { useCart } from '../hooks/useCart';
import { createAddress, listAddresses } from '../services/addressApi';
import { getErrorMessage } from '../services/errorMessage';
import { formatPrice } from '../services/format';
import { getCheckoutPreview, newIdempotencyKey, placeOrder } from '../services/orderApi';

// Phí vận chuyển (BR-35): miễn phí khi tổng tiền hàng từ mức này; backend tính lại khi đặt.
const FREE_SHIPPING_THRESHOLD = 500_000;

/**
 * Trang thanh toán /checkout (US-09, US-10): chọn hoặc thêm địa chỉ giao hàng, chọn phương thức
 * thanh toán, xem tóm tắt đơn theo giá hiện tại rồi xác nhận.
 *
 * Mã định danh lần đặt hàng (BR-10) được sinh 1 lần khi mở trang: bấm lại sau khi mất mạng gửi
 * cùng mã, backend trả lại đơn đã tạo thay vì tạo thêm đơn.
 */
function CheckoutPage() {
  const navigate = useNavigate();
  const { refresh: refreshCart } = useCart();
  const preview = useApiData('checkout-preview', getCheckoutPreview);
  const addressBook = useApiData('addresses', listAddresses);
  const [idempotencyKey] = useState(newIdempotencyKey);
  const [selectedId, setSelectedId] = useState(null);
  const [addingAddress, setAddingAddress] = useState(false);
  const [placing, setPlacing] = useState(false);
  const [placeError, setPlaceError] = useState('');

  const summary = preview.data ?? preview.lastData;
  const addresses = addressBook.data ?? addressBook.lastData;
  // Chưa chọn thì dùng địa chỉ mặc định (đứng đầu danh sách).
  const selected =
    addresses?.find((address) => address.id === selectedId) ?? addresses?.[0] ?? null;

  if (preview.error && !summary) {
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(preview.error, 'Không tải được đơn hàng')}</p>
        <button type="button" className="btn btn-ghost" onClick={preview.reload}>
          Thử lại
        </button>
      </div>
    );
  }
  if (!summary) return <p className="muted">Đang tải...</p>;
  if (summary.items.length === 0) {
    return (
      <div className="empty-state">
        <h1>Thanh toán</h1>
        <p>Giỏ hàng của bạn đang trống.</p>
        <Link to="/products" className="btn btn-primary">
          Xem sản phẩm
        </Link>
      </div>
    );
  }

  const handleAddAddress = async (values) => {
    const created = await createAddress(values);
    setSelectedId(created.id);
    setAddingAddress(false);
    addressBook.reload();
  };

  const handlePlaceOrder = async () => {
    setPlaceError('');
    setPlacing(true);
    try {
      const order = await placeOrder({
        idempotencyKey,
        paymentMethod: 'COD',
        addressId: selected.id,
        expectedTotal: summary.total,
      });
      refreshCart().catch(() => {});
      navigate(`/orders/${order.id}`, { replace: true, state: { placed: true } });
    } catch (err) {
      setPlaceError(getErrorMessage(err, 'Không đặt được đơn hàng'));
      setPlacing(false);
      // Giỏ hàng hoặc giá vừa đổi: tải lại tóm tắt để khách xem con số mới.
      if (err.response?.status === 409) preview.reload();
    }
  };

  const missing = FREE_SHIPPING_THRESHOLD - summary.subtotal;

  return (
    <div className="checkout-page">
      <h1>Thanh toán</h1>
      {summary.notices.length > 0 && (
        <div className="alert alert-warning" role="status">
          <ul className="notice-list">
            {summary.notices.map((notice) => (
              <li key={`${notice.variantId}:${notice.type}`}>
                {notice.productName && <strong>{notice.productName}: </strong>}
                {notice.message}
              </li>
            ))}
          </ul>
        </div>
      )}
      <div className="checkout-layout">
        <div className="checkout-main">
          <section className="checkout-section">
            <h2>Địa chỉ giao hàng</h2>
            {!addresses && addressBook.error && (
              <p className="alert alert-error">
                {getErrorMessage(addressBook.error, 'Không tải được sổ địa chỉ')}
              </p>
            )}
            {addresses && addresses.length > 0 && (
              <ul className="address-list">
                {addresses.map((address) => (
                  <li key={address.id}>
                    <label className="address-option">
                      <input
                        type="radio"
                        name="address"
                        checked={selected?.id === address.id}
                        onChange={() => setSelectedId(address.id)}
                      />
                      <AddressText address={address} />
                    </label>
                  </li>
                ))}
              </ul>
            )}
            {addresses && (addingAddress || addresses.length === 0) ? (
              <AddressForm
                initial={{
                  recipientName: '',
                  phone: '',
                  addressDetail: '',
                  isDefault: addresses.length === 0,
                }}
                submitLabel="Lưu địa chỉ"
                onSubmit={handleAddAddress}
                onCancel={addresses.length > 0 ? () => setAddingAddress(false) : undefined}
              />
            ) : (
              <p className="checkout-links">
                <button type="button" className="btn-link" onClick={() => setAddingAddress(true)}>
                  + Thêm địa chỉ mới
                </button>
                <Link to="/addresses" className="small">
                  Quản lý sổ địa chỉ
                </Link>
              </p>
            )}
          </section>
          <section className="checkout-section">
            <h2>Phương thức thanh toán</h2>
            <label className="payment-option">
              <input type="radio" name="payment" checked readOnly />
              Thanh toán khi nhận hàng (COD)
            </label>
            <label className="payment-option muted">
              <input type="radio" name="payment" disabled />
              Thanh toán online qua VNPay (sắp có)
            </label>
          </section>
          <section className="checkout-section">
            <h2>Sản phẩm</h2>
            <ul className="order-lines">
              {summary.items.map((item) => (
                <li key={item.variantId} className="order-line">
                  <div>
                    <Link to={`/products/${item.productId}`}>{item.productName}</Link>
                    {item.variantLabel && (
                      <p className="muted small">Phân loại: {item.variantLabel}</p>
                    )}
                  </div>
                  <span className="muted small">
                    {formatPrice(item.unitPrice)} × {item.quantity}
                  </span>
                  <strong>{formatPrice(item.lineTotal)}</strong>
                </li>
              ))}
            </ul>
            <Link to="/cart" className="small">
              Sửa giỏ hàng
            </Link>
          </section>
        </div>
        <aside className="cart-summary">
          <p className="cart-summary-row">
            <span>Tổng tiền hàng</span>
            <span>{formatPrice(summary.subtotal)}</span>
          </p>
          <p className="cart-summary-row">
            <span>Phí vận chuyển</span>
            <span>{summary.shippingFee === 0 ? 'Miễn phí' : formatPrice(summary.shippingFee)}</span>
          </p>
          {missing > 0 && (
            <p className="muted small">
              Mua thêm {formatPrice(missing)} để được miễn phí vận chuyển.
            </p>
          )}
          <p className="cart-summary-row cart-summary-total">
            <span>Tổng thanh toán</span>
            <strong>{formatPrice(summary.total)}</strong>
          </p>
          {placeError && (
            <p className="alert alert-error small" role="alert">
              {placeError}
            </p>
          )}
          <button
            type="button"
            className="btn btn-primary btn-block"
            disabled={placing || !selected || preview.loading}
            onClick={handlePlaceOrder}
          >
            {placing ? 'Đang đặt hàng...' : 'Đặt hàng'}
          </button>
          {!selected && <p className="muted small">Hãy thêm địa chỉ giao hàng trước khi đặt.</p>}
        </aside>
      </div>
    </div>
  );
}

export default CheckoutPage;
