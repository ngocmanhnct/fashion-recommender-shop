import ProductCard from './ProductCard';

/**
 * Lưới thẻ sản phẩm, tự đổi số cột theo bề rộng chỗ đặt lưới (2 cột trên điện thoại, tối đa 4).
 * reasons: lý do gợi ý theo mã sản phẩm (danh sách liên quan), không bắt buộc.
 */
function ProductGrid({ products, reasons }) {
  return (
    <div className="product-grid-container">
      <ul className="product-grid">
        {products.map((product) => (
          <li key={product.id}>
            <ProductCard product={product} reason={reasons?.[product.id]} />
          </li>
        ))}
      </ul>
    </div>
  );
}

export default ProductGrid;
