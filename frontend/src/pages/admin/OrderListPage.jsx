import { Link, useSearchParams } from 'react-router';
import Pagination from '../../components/Pagination';
import { useApiData } from '../../hooks/useApiData';
import { getErrorMessage } from '../../services/errorMessage';
import { formatPrice } from '../../services/format';
import { searchOrders } from '../../services/orderAdminApi';
import { formatDateTime, ORDER_STATUS_LABELS } from '../../services/orderLabels';

const STATUS_TABS = ['', 'PENDING', 'PROCESSING', 'SHIPPING', 'DELIVERED', 'CANCELLED'];

/** Đọc bộ lọc từ URL (?status=&keyword=&page=, page đếm từ 1) để F5 vẫn giữ bộ lọc. */
function readFilters(searchParams) {
  const status = searchParams.get('status') ?? '';
  const pageParam = Number.parseInt(searchParams.get('page') ?? '1', 10);
  return {
    status: STATUS_TABS.includes(status) ? status : '',
    keyword: searchParams.get('keyword') ?? '',
    page: Number.isInteger(pageParam) && pageParam > 0 ? pageParam - 1 : 0,
  };
}

/** Trang quản trị /admin/orders (US-17): danh sách đơn, lọc theo trạng thái, tìm theo mã/email/SĐT. */
function OrderListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = readFilters(searchParams);
  const { data, lastData, error, loading, reload } = useApiData(JSON.stringify(filters), (key) =>
    searchOrders(JSON.parse(key))
  );
  const result = data ?? lastData;

  const update = (next) => {
    const merged = { ...filters, ...next };
    const params = {};
    if (merged.status) params.status = merged.status;
    if (merged.keyword) params.keyword = merged.keyword;
    if (merged.page > 0) params.page = String(merged.page + 1);
    setSearchParams(params);
  };

  const handleSearch = (event) => {
    event.preventDefault();
    const keyword = new FormData(event.currentTarget).get('keyword').trim();
    update({ keyword, page: 0 });
  };

  return (
    <div>
      <div className="page-header">
        <h1>Đơn hàng</h1>
      </div>

      <div className="status-tabs" role="tablist">
        {STATUS_TABS.map((status) => (
          <button
            key={status || 'ALL'}
            type="button"
            role="tab"
            aria-selected={filters.status === status}
            className={filters.status === status ? 'btn btn-primary' : 'btn btn-ghost'}
            onClick={() => update({ status, page: 0 })}
          >
            {status ? ORDER_STATUS_LABELS[status] : 'Tất cả'}
          </button>
        ))}
      </div>

      <form className="inline-form" onSubmit={handleSearch}>
        <input
          key={filters.keyword}
          name="keyword"
          placeholder="Mã đơn, email khách hoặc số điện thoại"
          defaultValue={filters.keyword}
        />
        <button type="submit" className="btn btn-ghost">
          Tìm
        </button>
      </form>

      {error && (
        <div className="alert alert-error">
          <p>{getErrorMessage(error, 'Không tải được danh sách đơn hàng')}</p>
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Thử lại
          </button>
        </div>
      )}
      {!result && loading && <p className="muted">Đang tải...</p>}

      {result && (
        <>
          <p className="muted small">{result.totalElements} đơn hàng</p>
          <table className="table">
            <thead>
              <tr>
                <th>Mã đơn</th>
                <th>Ngày đặt</th>
                <th>Khách hàng</th>
                <th>Người nhận</th>
                <th>Số lượng</th>
                <th>Tổng tiền</th>
                <th>Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {result.content.map((o) => (
                <tr key={o.id}>
                  <td>
                    <Link to={`/admin/orders/${o.id}`}>#{o.id}</Link>
                  </td>
                  <td>{formatDateTime(o.createdAt)}</td>
                  <td>
                    {o.customerName}
                    <div className="muted small">{o.customerEmail}</div>
                  </td>
                  <td>
                    {o.recipientName}
                    <div className="muted small">{o.phone}</div>
                  </td>
                  <td>{o.totalQuantity}</td>
                  <td>{formatPrice(o.total)}</td>
                  <td>
                    <span className={`status status-${o.status.toLowerCase()}`}>
                      {ORDER_STATUS_LABELS[o.status]}
                    </span>
                  </td>
                </tr>
              ))}
              {result.content.length === 0 && (
                <tr>
                  <td colSpan={7} className="muted">
                    Không có đơn hàng nào.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            onChange={(page) => update({ page })}
          />
        </>
      )}
    </div>
  );
}

export default OrderListPage;
