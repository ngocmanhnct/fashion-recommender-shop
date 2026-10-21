import { useApiData } from '../hooks/useApiData';
import { getProductSuggestions } from '../services/productApi';
import ProductGrid from './ProductGrid';

/**
 * "Có thể bạn quan tâm" khi tìm không có kết quả (US-02): sản phẩm còn hàng khớp ít nhất 1 từ của
 * từ khóa, không có thì sản phẩm bán chạy. Lỗi hoặc không có gợi ý nào thì ẩn cả khối.
 */
function ProductSuggestions({ keyword }) {
  const { data, loading } = useApiData(keyword, getProductSuggestions);

  if (loading || !data || data.length === 0) return null;
  return (
    <section className="product-section">
      <h2>Có thể bạn quan tâm</h2>
      <ProductGrid products={data} />
    </section>
  );
}

export default ProductSuggestions;
