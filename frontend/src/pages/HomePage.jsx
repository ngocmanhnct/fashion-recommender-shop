import { Link } from 'react-router';
import ProductSection from '../components/ProductSection';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { getCategories } from '../services/catalogApi';
import { productsPath } from '../services/productQuery';

/**
 * Trang chủ: lời chào ngắn, lối tắt vào từng danh mục và khối "Sản phẩm bán chạy" (bán chạy =
 * tổng số lượng đã bán của các đơn không bị hủy, BR-22).
 */
function HomePage() {
  const { user } = useAuth();
  return (
    <div>
      <section className="home-hero">
        <h1>Fashion Shop</h1>
        <p className="muted">
          Giày dép, quần áo và phụ kiện thời trang, kèm gợi ý sản phẩm phù hợp với bạn.
        </p>
        <p>
          <Link to="/products" className="btn btn-primary">
            Xem tất cả sản phẩm
          </Link>
          {user?.role === 'ADMIN' && (
            <Link to="/admin" className="btn btn-ghost">
              Vào trang quản trị
            </Link>
          )}
        </p>
      </section>

      <CategoryChips />
      <ProductSection title="Sản phẩm bán chạy" sort="POPULAR" size={8} moreLink="/products" />

      <p className="muted small home-footer">
        <Link to="/health">Kiểm tra kết nối backend</Link>
      </p>
    </div>
  );
}

/** Lối tắt vào từng danh mục; ẩn khi chưa tải được hoặc chưa có danh mục nào. */
function CategoryChips() {
  const { data } = useApiData('all', getCategories);
  if (!data || data.length === 0) return null;
  return (
    <nav className="category-chips" aria-label="Danh mục sản phẩm">
      {data.map((category) => (
        <Link
          key={category.id}
          to={productsPath({ categoryId: String(category.id) })}
          className="chip"
        >
          {category.name}
        </Link>
      ))}
    </nav>
  );
}

export default HomePage;
