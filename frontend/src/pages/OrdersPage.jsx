import { Link, useSearchParams } from 'react-router';
import Pagination from '../components/Pagination';
import { useApiData } from '../hooks/useApiData';
import { getErrorMessage } from '../services/errorMessage';
import { formatPrice } from '../services/format';
import { listOrders } from '../services/orderApi';
import { formatDateTime, ORDER_STATUS_LABELS } from '../services/orderLabels';

const PAGE_SIZE = 10;

/** Trang "Đơn hàng của tôi" /orders (US-11): mới nhất trước, trang lưu ở ?page= (đếm từ 1). */
function OrdersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const pageParam = Number.parseInt(searchParams.get('page') ?? '1', 10);
  const page = Number.isInteger(pageParam) && pageParam > 0 ? pageParam - 1 : 0;
  const { data, lastData, error, loading, reload } = useApiData(page, (p) =>
    listOrders(p, PAGE_SIZE)
  );
  const orders = data ?? lastData;

  const changePage = (next) => {
    setSearchParams(next === 0 ? {} : { page: String(next + 1) });
  };

  return (
    <div className="narrow-page">
      <h1>Đơn hàng của tôi</h1>
      <p className="small">
        <Link to="/addresses">Sổ địa chỉ</Link>
      </p>
      {error && (
        <div className="alert alert-error">
          <p>{getErrorMessage(error, 'Không tải được đơn hàng')}</p>
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Thử lại
          </button>
        </div>
      )}
      {!orders && loading && <p className="muted">Đang tải...</p>}
      {orders && orders.totalElements === 0 && (
        <div className="empty-state">
          <p>Bạn chưa có đơn hàng nào.</p>
          <Link to="/products" className="btn btn-primary">
            Xem sản phẩm
          </Link>
        </div>
      )}
      {orders && orders.content.length > 0 && (
        <>
          <ul className="order-list">
            {orders.content.map((order) => (
              <li key={order.id}>
                <Link to={`/orders/${order.id}`} className="order-card">
                  <div className="order-card-head">
                    <strong>Đơn #{order.id}</strong>
                    <span className={`status status-${order.status.toLowerCase()}`}>
                      {ORDER_STATUS_LABELS[order.status]}
                    </span>
                  </div>
                  <p className="small">
                    {order.firstProductName}
                    {order.itemCount > 1 && ` và ${order.itemCount - 1} sản phẩm khác`}
                  </p>
                  <div className="order-card-head muted small">
                    <span>
                      {formatDateTime(order.createdAt)} · {order.totalQuantity} sản phẩm
                    </span>
                    <strong>{formatPrice(order.total)}</strong>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <Pagination page={orders.page} totalPages={orders.totalPages} onChange={changePage} />
        </>
      )}
    </div>
  );
}

export default OrdersPage;
