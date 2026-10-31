import { useApiData } from '../hooks/useApiData';
import { getBoughtTogether } from '../services/productApi';
import RecommendationGrid from './RecommendationGrid';

/** Mã sản phẩm khác nhau, tăng dần, nối bằng dấu phẩy: đổi số lượng không làm tải lại. */
function cartKey(items) {
  const ids = [...new Set(items.map((item) => item.productId))].sort((a, b) => a - b);
  return ids.length > 0 ? ids.join(',') : null;
}

function load(key) {
  return getBoughtTogether(key.split(',').map(Number));
}

/**
 * "Thường được mua cùng" ở trang giỏ hàng (chức năng 20): tối đa 4 sản phẩm hay được mua kèm các
 * sản phẩm trong giỏ. Chỉ tải lại khi tập sản phẩm trong giỏ đổi; đang tải, lỗi hoặc chưa ai mua
 * cùng thì ẩn cả khối (không lấp bằng sản phẩm bán chạy).
 */
function BoughtTogether({ items }) {
  const { data, error, loading } = useApiData(cartKey(items), load);

  if (loading || error || !data || data.length === 0) return null;

  return (
    <section className="product-section">
      <h2>Thường được mua cùng</h2>
      <RecommendationGrid items={data} placement="CART" />
    </section>
  );
}

export default BoughtTogether;
