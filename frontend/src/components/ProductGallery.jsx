import { useState } from 'react';
import ProductImage from './ProductImage';

/**
 * Ảnh lớn của sản phẩm và dãy ảnh nhỏ để đổi ảnh lớn (chỉ hiện khi có từ 2 ảnh khác nhau). Chưa có
 * ảnh thì hiện ảnh minh họa theo danh mục, kèm dòng ghi rõ đó là ảnh minh họa.
 */
function ProductGallery({ imageUrls, productId, categoryName }) {
  const urls = [...new Set(imageUrls)];
  const [current, setCurrent] = useState(0);
  return (
    <div className="product-gallery">
      <ProductImage
        src={urls[current]}
        productId={productId}
        categoryName={categoryName}
        className="product-gallery-main"
      />
      {urls.length === 0 && (
        <p className="muted small product-gallery-note">
          Ảnh minh họa theo danh mục, không phải ảnh thật của sản phẩm.
        </p>
      )}
      {urls.length > 1 && (
        <div className="product-gallery-thumbs">
          {urls.map((url, index) => (
            <button
              key={url}
              type="button"
              className="product-gallery-thumb"
              aria-pressed={index === current}
              aria-label={`Ảnh ${index + 1}`}
              onClick={() => setCurrent(index)}
            >
              <ProductImage src={url} />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export default ProductGallery;
