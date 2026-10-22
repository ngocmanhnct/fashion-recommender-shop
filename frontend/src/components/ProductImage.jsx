import ProductArt from './ProductArt';

/**
 * Ảnh sản phẩm, hoặc ảnh minh họa vẽ theo danh mục khi sản phẩm chưa có ảnh (danh mục mẫu chưa có
 * ảnh nào). productId, categoryName dùng cho ảnh minh họa; thiếu productId thì hiện khung trống.
 * Ảnh chỉ để minh họa nên alt rỗng: tên sản phẩm luôn hiện ngay bên cạnh.
 */
function ProductImage({ src, productId, categoryName, className = '' }) {
  if (src) {
    return <img src={src} alt="" loading="lazy" className={`product-image ${className}`} />;
  }
  if (productId != null) {
    return <ProductArt productId={productId} categoryName={categoryName} className={className} />;
  }
  return <span className={`product-image product-image-empty ${className}`} aria-hidden="true" />;
}

export default ProductImage;
