import { Link } from 'react-router';
import { formatNumber, formatPriceRange, formatRating } from '../services/format';
import ProductImage from './ProductImage';

/**
 * Thẻ sản phẩm trong lưới: cả thẻ là 1 liên kết tới trang chi tiết (không lồng nút hay liên kết
 * khác bên trong). Điểm đánh giá chỉ hiện khi đã có đánh giá, số đã bán chỉ hiện khi lớn hơn 0.
 * reason: lý do gợi ý (danh sách sản phẩm liên quan), không bắt buộc.
 */
function ProductCard({ product, reason }) {
  const hasRating = product.ratingAverage != null;
  const hasSold = product.soldCount > 0;
  return (
    <Link to={`/products/${product.id}`} className="product-card">
      <span className="product-card-media">
        <ProductImage
          src={product.thumbnailUrl}
          productId={product.id}
          categoryName={product.categoryName}
        />
        {!product.inStock && <span className="badge badge-soldout">Hết hàng</span>}
      </span>
      <span className="product-card-body">
        <span className="product-card-name">{product.name}</span>
        <span className="product-card-price">
          {formatPriceRange(product.minPrice, product.maxPrice)}
        </span>
        {(hasRating || hasSold) && (
          <span className="product-card-meta">
            {hasRating && (
              <span>
                <span aria-hidden="true">★ </span>
                {formatRating(product.ratingAverage)}
                <span className="visually-hidden"> trên 5 sao</span>
              </span>
            )}
            {hasSold && <span>Đã bán {formatNumber(product.soldCount)}</span>}
          </span>
        )}
        {reason && <span className="product-card-reason">{reason}</span>}
      </span>
    </Link>
  );
}

export default ProductCard;
