import { Link } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { getErrorMessage } from '../services/errorMessage';
import { loadProductPage } from '../services/productQuery';
import ProductGrid from './ProductGrid';

/**
 * Một khối sản phẩm ở trang chủ: tiêu đề, các sản phẩm đầu tiên theo cách sắp xếp sort (tối đa
 * size sản phẩm) và liên kết "Xem tất cả". Khối không có sản phẩm nào thì ẩn hẳn.
 */
function ProductSection({ title, sort, size, moreLink }) {
  const requestKey = new URLSearchParams({ sort, size: String(size) }).toString();
  const { data, error, loading } = useApiData(requestKey, loadProductPage);

  if (!loading && !error && data.content.length === 0) return null;

  return (
    <section className="product-section">
      <div className="section-header">
        <h2>{title}</h2>
        {moreLink && <Link to={moreLink}>Xem tất cả ›</Link>}
      </div>
      <SectionContent data={data} error={error} loading={loading} />
    </section>
  );
}

function SectionContent({ data, error, loading }) {
  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="alert alert-error">{getErrorMessage(error)}</p>;
  return <ProductGrid products={data.content} />;
}

export default ProductSection;
