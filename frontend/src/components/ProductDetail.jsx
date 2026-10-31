import { useEffect, useRef } from 'react';
import { Link } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../services/errorMessage';
import { formatNumber, formatRating } from '../services/format';
import { getProduct, recordProductView } from '../services/productApi';
import { productsPath } from '../services/productQuery';
import ProductGallery from './ProductGallery';
import ProductNotFound from './ProductNotFound';
import ProductReviews from './ProductReviews';
import RelatedProducts from './RelatedProducts';
import VariantPicker from './VariantPicker';
import WishlistButton from './WishlistButton';

/**
 * Nội dung trang chi tiết sản phẩm (US-03): đường dẫn, ảnh, tên, thương hiệu, giá, chọn phân
 * loại, nút yêu thích, mô tả, đánh giá và gợi ý sản phẩm liên quan. Trang tạo lại component này theo mã sản phẩm (key),
 * nên chuyển sang sản phẩm khác (vd bấm 1 sản phẩm liên quan) bắt đầu lại từ đầu, không còn sót
 * dữ liệu hay lựa chọn của sản phẩm trước.
 *
 * Khách hàng đã đăng nhập: ghi 1 lượt xem cho mỗi lần mở trang (dữ liệu cho gợi ý, BR-19).
 */
function ProductDetail({ id }) {
  const { user, loading: authLoading } = useAuth();
  const { data: product, error, loading, reload } = useApiData(id, getProduct);
  // Mã sản phẩm đã ghi lượt xem: StrictMode chạy effect 2 lần, vẫn chỉ gửi 1 request.
  const loggedIdRef = useRef(null);

  useEffect(() => {
    if (authLoading || !product || user?.role !== 'CUSTOMER') return;
    if (loggedIdRef.current === product.id) return;
    loggedIdRef.current = product.id;
    recordProductView(product.id).catch(() => {
      /* Lượt xem chỉ phục vụ gợi ý; lỗi không ảnh hưởng người xem. */
    });
  }, [authLoading, product, user]);

  if (loading) return <p className="muted">Đang tải sản phẩm...</p>;
  if (error) {
    const status = error.response?.status;
    if (status === 404 || status === 400) return <ProductNotFound />;
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không tải được sản phẩm, vui lòng thử lại')}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div>
      <nav className="breadcrumb" aria-label="Đường dẫn">
        <Link to="/">Trang chủ</Link>
        <span aria-hidden="true"> › </span>
        <Link to={productsPath({ categoryId: String(product.category.id) })}>
          {product.category.name}
        </Link>
        <span aria-hidden="true"> › </span>
        <span aria-current="page">{product.name}</span>
      </nav>

      <div className="product-detail">
        <ProductGallery
          imageUrls={product.imageUrls}
          productId={product.id}
          categoryName={product.category?.name}
        />
        <div className="product-info">
          <h1>{product.name}</h1>
          {product.brand && (
            <p className="muted">
              Thương hiệu:{' '}
              <Link to={productsPath({ brandId: String(product.brand.id) })}>
                {product.brand.name}
              </Link>
            </p>
          )}
          <ProductStats product={product} />
          <VariantPicker key={product.id} product={product} />
          <WishlistButton productId={product.id} />
        </div>
      </div>

      {product.description && (
        <section className="product-description">
          <h2>Mô tả sản phẩm</h2>
          <p>{product.description}</p>
        </section>
      )}

      <ProductReviews productId={product.id} />
      <RelatedProducts productId={id} />
    </div>
  );
}

/** Điểm đánh giá và số đã bán, chỉ hiện phần đã có số liệu. */
function ProductStats({ product }) {
  const hasRating = product.ratingAverage != null;
  const hasSold = product.soldCount > 0;
  if (!hasRating && !hasSold) return null;
  return (
    <p className="product-stats">
      {hasRating && (
        <span>
          <span aria-hidden="true">★ </span>
          {formatRating(product.ratingAverage)}/5 ({formatNumber(product.reviewCount)} đánh giá)
        </span>
      )}
      {hasSold && <span>Đã bán {formatNumber(product.soldCount)}</span>}
    </p>
  );
}

export default ProductDetail;
