import { useState } from 'react';
import { categoryPhotoUrl } from '../services/categoryPhotos';
import ProductArt from './ProductArt';

/**
 * Ảnh sản phẩm. Sản phẩm chưa có ảnh (danh mục mẫu chưa có ảnh nào) thì hiện ảnh minh họa của danh
 * mục, danh mục chưa có ảnh minh họa hoặc ảnh tải lỗi thì hiện hình vẽ SVG theo danh mục.
 * productId, categoryName dùng cho ảnh minh họa; thiếu productId thì hiện khung trống.
 * Ảnh chỉ để minh họa nên alt rỗng: tên sản phẩm luôn hiện ngay bên cạnh.
 */
function ProductImage({ src, productId, categoryName, className = '' }) {
  const url = src || categoryPhotoUrl(categoryName, productId);
  // Nhớ đường dẫn bị lỗi (không phải cờ true/false) để đổi sang ảnh khác thì thử tải lại.
  const [failedUrl, setFailedUrl] = useState(null);

  if (url && url !== failedUrl) {
    return (
      <img
        src={url}
        alt=""
        loading="lazy"
        className={`product-image ${className}`}
        onError={() => setFailedUrl(url)}
      />
    );
  }
  if (productId != null) {
    return <ProductArt productId={productId} categoryName={categoryName} className={className} />;
  }
  return <span className={`product-image product-image-empty ${className}`} aria-hidden="true" />;
}

export default ProductImage;
