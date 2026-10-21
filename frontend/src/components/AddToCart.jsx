import { useState } from 'react';
import { Link } from 'react-router';
import { useCart } from '../hooks/useCart';
import { getErrorMessage } from '../services/errorMessage';
import { MAX_QUANTITY } from '../services/guestCart';
import QuantityStepper from './QuantityStepper';

/**
 * Chọn số lượng và thêm vào giỏ hàng ở trang chi tiết (US-06). Số lượng chọn được tối đa bằng
 * tồn kho trừ số đã có trong giỏ (chỉ để tham khảo; tồn kho chỉ bị trừ khi đặt hàng, BR-08).
 * VariantPicker tạo lại component này mỗi khi đổi biến thể (key), nên số lượng và thông báo cũ
 * không áp sang biến thể khác.
 *
 * @param variant biến thể đang chọn, null nếu còn tầng phân loại chưa chọn
 * @param missingTiers tên các tầng chưa chọn, vd ["Kích cỡ"]
 */
function AddToCart({ variant, missingTiers }) {
  const { mode, cart, addItem } = useCart();
  const [quantity, setQuantity] = useState(1);
  const [status, setStatus] = useState({ type: 'idle', message: '' });

  if (mode === 'none') {
    return <p className="muted">Tài khoản quản trị không dùng giỏ hàng.</p>;
  }

  const inCart = variant
    ? (cart?.items.find((item) => item.variantId === variant.id)?.quantity ?? 0)
    : 0;
  const available = variant ? Math.min(variant.stock, MAX_QUANTITY) - inCart : MAX_QUANTITY;
  const submitting = status.type === 'submitting';
  const effectiveQuantity = Math.min(quantity, Math.max(available, 1));

  const handleAdd = async () => {
    if (!variant) {
      setStatus({
        type: 'error',
        message: `Vui lòng chọn ${missingTiers.join(' và ').toLowerCase()}`,
      });
      return;
    }
    setStatus({ type: 'submitting', message: '' });
    try {
      await addItem(variant.id, effectiveQuantity);
      setQuantity(1);
      setStatus({ type: 'success', message: 'Đã thêm vào giỏ hàng.' });
    } catch (err) {
      setStatus({ type: 'error', message: getErrorMessage(err, 'Không thêm được vào giỏ hàng') });
    }
  };

  return (
    <div className="add-to-cart">
      {variant && available <= 0 ? (
        <p className="muted">
          Giỏ hàng đã có {inCart} sản phẩm này, bằng số lượng còn lại.{' '}
          <Link to="/cart">Xem giỏ hàng</Link>
        </p>
      ) : (
        <div className="add-to-cart-row">
          <QuantityStepper
            value={effectiveQuantity}
            max={Math.max(available, 1)}
            onChange={setQuantity}
            disabled={submitting}
            label="Số lượng"
          />
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleAdd}
            disabled={submitting || mode === null}
          >
            {submitting ? 'Đang thêm...' : 'Thêm vào giỏ hàng'}
          </button>
        </div>
      )}
      {variant && inCart > 0 && available > 0 && (
        <p className="muted small">Giỏ hàng đã có {inCart} sản phẩm này.</p>
      )}
      {status.type === 'success' && (
        <p className="alert alert-success" role="status">
          {status.message} <Link to="/cart">Xem giỏ hàng</Link>
        </p>
      )}
      {status.type === 'error' && (
        <p className="alert alert-error" role="alert">
          {status.message}
        </p>
      )}
    </div>
  );
}

export default AddToCart;
