import { useRef } from 'react';
import { Link, useSearchParams } from 'react-router';
import Pagination from '../components/Pagination';
import ProductFilters from '../components/ProductFilters';
import ProductGrid from '../components/ProductGrid';
import ProductSuggestions from '../components/ProductSuggestions';
import { useApiData } from '../hooks/useApiData';
import { getErrorMessage } from '../services/errorMessage';
import { formatNumber } from '../services/format';
import {
  countActiveFilters,
  loadProductPage,
  parseProductQuery,
  productsPath,
  toSearchParams,
} from '../services/productQuery';

/**
 * Trang danh sách sản phẩm /products (US-01, US-02): tìm theo từ khóa, lọc, sắp xếp, phân trang.
 * URL là nơi lưu điều kiện lọc: chia sẻ hay tải lại trang vẫn giữ nguyên kết quả. Mỗi thay đổi
 * bộ lọc là 1 lần setSearchParams và luôn về trang 1.
 */
function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const criteria = parseProductQuery(searchParams);
  const requestKey = toSearchParams(criteria).toString();
  const result = useApiData(requestKey, loadProductPage);
  const resultsRef = useRef(null);

  const applyFilters = (changes) => {
    setSearchParams(toSearchParams({ ...criteria, ...changes, page: 0 }));
  };

  const goToPage = (page) => {
    setSearchParams(toSearchParams({ ...criteria, page }));
    // Nút chuyển trang nằm cuối danh sách: cuộn lên đầu kết quả nếu đầu kết quả đã khuất.
    const results = resultsRef.current;
    if (results && results.getBoundingClientRect().top < 0) {
      results.scrollIntoView();
    }
  };

  const title = criteria.keyword ? `Kết quả tìm kiếm cho “${criteria.keyword}”` : 'Sản phẩm';

  return (
    <div className="shop">
      <aside className="shop-filters" aria-label="Bộ lọc sản phẩm">
        <ProductFilters criteria={criteria} onChange={applyFilters} />
      </aside>
      <section className="shop-results" ref={resultsRef} aria-busy={result.loading}>
        <h1>{title}</h1>
        <ProductResults criteria={criteria} result={result} onPageChange={goToPage} />
      </section>
    </div>
  );
}

/**
 * Kết quả của trang danh sách. Trong lúc tải trang hoặc bộ lọc mới, danh sách cũ vẫn hiện (mờ đi)
 * thay vì biến mất rồi hiện lại.
 */
function ProductResults({ criteria, result, onPageChange }) {
  const { data, error, loading, lastData, reload } = result;
  if (error) {
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error)}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }
  const shown = loading && lastData?.content?.length > 0 ? lastData : data;
  if (!shown) {
    return <p className="muted">Đang tải sản phẩm...</p>;
  }
  if (shown.totalElements === 0) {
    return <EmptyResult criteria={criteria} />;
  }
  if (shown.content.length === 0) {
    return (
      <p className="alert alert-warning">
        Trang này không có sản phẩm nào.{' '}
        <Link to={productsPath({ ...criteria, page: 0 })}>Về trang đầu</Link>
      </p>
    );
  }
  return (
    <div className={loading ? 'results is-loading' : 'results'}>
      <p className="muted small">{formatNumber(shown.totalElements)} sản phẩm</p>
      <ProductGrid products={shown.content} />
      <Pagination page={shown.page} totalPages={shown.totalPages} onChange={onPageChange} />
    </div>
  );
}

/**
 * Không có kết quả (US-02): báo rõ, cho cách thoát (bỏ bộ lọc nhưng giữ từ khóa, hoặc xem tất cả
 * sản phẩm) và gợi ý vài sản phẩm liên quan tới từ khóa.
 */
function EmptyResult({ criteria }) {
  const hasFilters = countActiveFilters(criteria) > 0;
  return (
    <>
      <div className="empty-state">
        <h2>
          Không tìm thấy kết quả
          {criteria.keyword ? ` cho “${criteria.keyword}”` : ''}
        </h2>
        <p className="muted">
          {hasFilters
            ? 'Thử bỏ bớt bộ lọc hoặc tìm bằng từ khóa khác.'
            : 'Thử tìm bằng từ khóa khác hoặc ngắn hơn.'}
        </p>
        {hasFilters && (
          <Link
            to={productsPath({ keyword: criteria.keyword, sort: criteria.sort })}
            className="btn btn-primary"
          >
            Xóa bộ lọc
          </Link>
        )}
        {!hasFilters && criteria.keyword !== '' && (
          <Link to="/products" className="btn btn-primary">
            Xem tất cả sản phẩm
          </Link>
        )}
      </div>
      <ProductSuggestions keyword={criteria.keyword} />
    </>
  );
}

export default ProductsPage;
