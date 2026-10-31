import { useState } from 'react';
import { Link } from 'react-router';
import ProductCard from '../components/ProductCard';
import { useApiData } from '../hooks/useApiData';
import { getErrorMessage } from '../services/errorMessage';
import { getWishlist, removeFromWishlist } from '../services/wishlistApi';

/**
 * Trang "Yêu thích" (US-14): các sản phẩm khách hàng đã lưu, thêm sau đứng trước, mỗi sản phẩm có
 * nút bỏ khỏi danh sách. Sản phẩm hết hàng vẫn hiện (thẻ ghi "Hết hàng"), sản phẩm đã ngừng bán
 * thì không.
 */
function WishlistPage() {
  const { data, error, loading, reload } = useApiData('wishlist', getWishlist);
  // Mã các sản phẩm vừa bỏ khỏi danh sách, ẩn ngay không cần tải lại.
  const [removed, setRemoved] = useState(() => new Set());
  const [removeError, setRemoveError] = useState('');

  const remove = async (productId) => {
    setRemoveError('');
    try {
      await removeFromWishlist(productId);
      setRemoved((prev) => new Set(prev).add(productId));
    } catch (err) {
      setRemoveError(getErrorMessage(err, 'Không bỏ được sản phẩm khỏi danh sách yêu thích'));
    }
  };

  return (
    <div>
      <h1>Sản phẩm yêu thích</h1>
      {removeError && (
        <p className="alert alert-error" role="alert">
          {removeError}
        </p>
      )}
      <WishlistContent
        items={data?.filter((item) => !removed.has(item.product.id))}
        loading={loading}
        error={error}
        onRetry={reload}
        onRemove={remove}
      />
    </div>
  );
}

function WishlistContent({ items, loading, error, onRetry, onRemove }) {
  if (loading) return <p className="muted">Đang tải danh sách yêu thích...</p>;
  if (error) {
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không tải được danh sách yêu thích')}</p>
        <button type="button" className="btn btn-ghost" onClick={onRetry}>
          Thử lại
        </button>
      </div>
    );
  }
  if (items.length === 0) {
    return (
      <div className="empty-state">
        <p>Bạn chưa lưu sản phẩm nào. Bấm "Yêu thích" ở trang sản phẩm để xem lại sau.</p>
        <Link to="/products" className="btn btn-primary">
          Xem sản phẩm
        </Link>
      </div>
    );
  }
  return (
    <div className="product-grid-container">
      <ul className="product-grid">
        {items.map(({ product }) => (
          <li key={product.id} className="wishlist-item">
            <ProductCard product={product} />
            <button
              type="button"
              className="btn-link small"
              onClick={() => onRemove(product.id)}
              aria-label={`Bỏ ${product.name} khỏi danh sách yêu thích`}
            >
              Bỏ yêu thích
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default WishlistPage;
