import { useApiData } from '../hooks/useApiData';
import { getRelatedProducts } from '../services/productApi';
import RecommendationGrid from './RecommendationGrid';

/**
 * "Gợi ý sản phẩm liên quan" ở trang chi tiết (BR-21): tối đa 4 sản phẩm, mỗi sản phẩm kèm lý do
 * gợi ý. Hiện đúng số sản phẩm backend trả về; lỗi hoặc danh sách rỗng thì ẩn cả khối.
 */
function RelatedProducts({ productId }) {
  const { data, error, loading } = useApiData(productId, getRelatedProducts);

  if (loading) {
    return (
      <section className="product-section">
        <h2>Gợi ý sản phẩm liên quan</h2>
        <p className="muted small">Đang tải...</p>
      </section>
    );
  }
  if (error || data.length === 0) return null;

  return (
    <section className="product-section">
      <h2>Gợi ý sản phẩm liên quan</h2>
      <RecommendationGrid items={data} placement="PRODUCT_DETAIL" />
    </section>
  );
}

export default RelatedProducts;
