import { Link } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { getErrorMessage } from '../services/errorMessage';
import { formatPrice } from '../services/format';
import { formatDateTime } from '../services/orderLabels';
import { getMyVouchers } from '../services/voucherApi';
import { describeDiscount } from '../services/voucherLabels';

/**
 * Trang "Ưu đãi của bạn" /offers (US-26): các mã giảm giá cửa hàng tặng riêng cho khách và còn dùng
 * được, hết hạn sớm nhất trước. Ở trang thanh toán khách bấm chọn mã, không cần gõ lại.
 */
function OffersPage() {
  const { data, error, loading, reload } = useApiData('my-vouchers', getMyVouchers);

  return (
    <div>
      <h1>Ưu đãi của bạn</h1>
      <p className="muted">
        Mã cửa hàng tặng riêng cho bạn. Ở bước thanh toán, bấm chọn mã để áp dụng.
      </p>
      {loading && <p className="muted">Đang tải...</p>}
      {error && (
        <div className="alert alert-error">
          <p>{getErrorMessage(error, 'Không tải được ưu đãi')}</p>
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Thử lại
          </button>
        </div>
      )}
      {data?.length === 0 && (
        <div className="empty-state">
          <p>Hiện bạn chưa có ưu đãi nào.</p>
          <Link to="/products" className="btn btn-primary">
            Xem sản phẩm
          </Link>
        </div>
      )}
      {data?.length > 0 && (
        <>
          <ul className="offer-list">
            {data.map((v) => (
              <li key={v.code} className="offer-card">
                <p className="offer-discount">{describeDiscount(v)}</p>
                <p className="small">
                  {v.minOrderAmount > 0
                    ? `Cho đơn từ ${formatPrice(v.minOrderAmount)}`
                    : 'Áp dụng cho mọi đơn'}
                </p>
                <p className="offer-code">
                  Mã <strong className="voucher-code">{v.code}</strong>
                </p>
                <p className="muted small">Hết hạn {formatDateTime(v.validUntil)}</p>
              </li>
            ))}
          </ul>
          <Link to="/products" className="btn btn-primary">
            Mua sắm ngay
          </Link>
        </>
      )}
    </div>
  );
}

export default OffersPage;
