import { Link, useLocation } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { getErrorMessage } from '../services/errorMessage';
import { confirmVnPayReturn } from '../services/orderApi';

/**
 * Trang VNPay chuyển khách về sau khi thanh toán /payment/vnpay-return (US-10). Gửi nguyên các
 * tham số VNPay gắn trên URL cho backend kiểm tra chữ ký và ghi nhận, rồi báo kết quả.
 */
function VnPayReturnPage() {
  const { search } = useLocation();
  const { data, error, loading, reload } = useApiData(
    search.includes('vnp_') ? search : null,
    confirmVnPayReturn
  );

  if (!search.includes('vnp_')) {
    return (
      <div className="empty-state">
        <h1>Không có kết quả thanh toán</h1>
        <Link to="/orders" className="btn btn-ghost">
          Xem đơn hàng của tôi
        </Link>
      </div>
    );
  }
  if (loading) return <p className="muted">Đang xác nhận kết quả thanh toán...</p>;
  if (error) {
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không xác nhận được kết quả thanh toán')}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="empty-state">
      <h1>{data.paid ? 'Thanh toán thành công' : 'Thanh toán chưa thành công'}</h1>
      <p className={data.paid ? 'alert alert-success' : 'alert alert-warning'} role="status">
        {data.message}
      </p>
      {data.orderId ? (
        <Link to={`/orders/${data.orderId}`} className="btn btn-primary">
          Xem đơn hàng #{data.orderId}
        </Link>
      ) : (
        <Link to="/orders" className="btn btn-ghost">
          Xem đơn hàng của tôi
        </Link>
      )}
    </div>
  );
}

export default VnPayReturnPage;
