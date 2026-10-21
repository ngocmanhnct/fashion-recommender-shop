/**
 * Ảnh sản phẩm, hoặc khung giữ chỗ khi sản phẩm chưa có ảnh (danh mục mẫu chưa có ảnh nào).
 * Ảnh chỉ để minh họa nên alt rỗng: tên sản phẩm luôn hiện ngay bên cạnh.
 */
function ProductImage({ src, className = '' }) {
  if (src) {
    return <img src={src} alt="" loading="lazy" className={`product-image ${className}`} />;
  }
  return (
    <span className={`product-image product-image-empty ${className}`} aria-hidden="true">
      <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor">
        <path
          strokeWidth="1.5"
          strokeLinejoin="round"
          d="M8 3 3.5 6 5 10l2-1v12h10V9l2 1 1.5-4L16 3c-.5 1.7-2 3-4 3S8.5 4.7 8 3Z"
        />
      </svg>
    </span>
  );
}

export default ProductImage;
