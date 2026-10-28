import { Link, useSearchParams } from 'react-router';
import Pagination from '../../components/Pagination';
import SegmentBadge from '../../components/SegmentBadge';
import { useApiData } from '../../hooks/useApiData';
import { getSegments, searchCustomers, UNSEGMENTED } from '../../services/customerAdminApi';
import { getErrorMessage } from '../../services/errorMessage';
import { formatDateTime } from '../../services/orderLabels';

/** Đọc bộ lọc từ URL (?keyword=&segment=&page=, page đếm từ 1) để F5 vẫn giữ bộ lọc. */
function readFilters(searchParams) {
  const pageParam = Number.parseInt(searchParams.get('page') ?? '1', 10);
  return {
    keyword: searchParams.get('keyword') ?? '',
    segment: searchParams.get('segment') ?? '',
    page: Number.isInteger(pageParam) && pageParam > 0 ? pageParam - 1 : 0,
  };
}

/** Trang quản trị /admin/customers (US-18): danh sách khách, tìm theo tên/email, lọc theo nhóm. */
function CustomerListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = readFilters(searchParams);
  const { data, lastData, error, loading, reload } = useApiData(JSON.stringify(filters), (key) =>
    searchCustomers(JSON.parse(key))
  );
  const { data: overview } = useApiData('segments', getSegments);
  const result = data ?? lastData;

  const update = (next) => {
    const merged = { ...filters, ...next };
    const params = {};
    if (merged.keyword) params.keyword = merged.keyword;
    if (merged.segment) params.segment = merged.segment;
    if (merged.page > 0) params.page = String(merged.page + 1);
    setSearchParams(params);
  };

  const handleSearch = (event) => {
    event.preventDefault();
    const keyword = new FormData(event.currentTarget).get('keyword').trim();
    update({ keyword, page: 0 });
  };

  const tabs = [
    { value: '', label: 'Tất cả' },
    ...(overview?.segments ?? []).map((s) => ({ value: s.code, label: s.name })),
    { value: UNSEGMENTED, label: 'Chưa đủ dữ liệu' },
  ];

  return (
    <div>
      <div className="page-header">
        <h1>Khách hàng</h1>
        <Link to="/admin/segments" className="btn btn-ghost">
          Xem phân nhóm
        </Link>
      </div>

      <div className="status-tabs" role="tablist">
        {tabs.map((tab) => (
          <button
            key={tab.value || 'ALL'}
            type="button"
            role="tab"
            aria-selected={filters.segment === tab.value}
            className={filters.segment === tab.value ? 'btn btn-primary' : 'btn btn-ghost'}
            onClick={() => update({ segment: tab.value, page: 0 })}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <form className="inline-form" onSubmit={handleSearch}>
        <input
          key={filters.keyword}
          name="keyword"
          placeholder="Tên hoặc email khách hàng"
          defaultValue={filters.keyword}
        />
        <button type="submit" className="btn btn-ghost">
          Tìm
        </button>
      </form>

      {error && (
        <div className="alert alert-error">
          <p>{getErrorMessage(error, 'Không tải được danh sách khách hàng')}</p>
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Thử lại
          </button>
        </div>
      )}
      {!result && loading && <p className="muted">Đang tải...</p>}

      {result && (
        <>
          <p className="muted small">{result.totalElements} khách hàng</p>
          <table className="table">
            <thead>
              <tr>
                <th>Khách hàng</th>
                <th>Ngày đăng ký</th>
                <th>Số đơn đã đặt</th>
                <th>Nhóm</th>
              </tr>
            </thead>
            <tbody>
              {result.content.map((c) => (
                <tr key={c.id}>
                  <td>
                    <Link to={`/admin/customers/${c.id}`}>{c.fullName}</Link>
                    <div className="muted small">{c.email}</div>
                  </td>
                  <td>{formatDateTime(c.createdAt)}</td>
                  <td>{c.orderCount}</td>
                  <td>
                    <SegmentBadge segment={c.segment} />
                  </td>
                </tr>
              ))}
              {result.content.length === 0 && (
                <tr>
                  <td colSpan={4} className="muted">
                    Không có khách hàng nào.
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

export default CustomerListPage;
