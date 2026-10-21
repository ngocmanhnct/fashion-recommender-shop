import { useApiData } from '../hooks/useApiData';
import { getRelatedProducts } from '../services/productApi';
import ProductGrid from './ProductGrid';

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

  const reasons = Object.fromEntries(data.map((item) => [item.product.id, item.reason]));
  return (
    <section className="product-section">
      <h2>Gợi ý sản phẩm liên quan</h2>
      <ProductGrid products={data.map((item) => item.product)} reasons={reasons} />
    </section>
  );
}

export default RelatedProducts;
