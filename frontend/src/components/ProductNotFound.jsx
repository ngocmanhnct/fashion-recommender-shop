import { Link } from 'react-router';

/** Trạng thái "Không tìm thấy sản phẩm": mã sai, sản phẩm không có hoặc đã ngừng bán. */
function ProductNotFound() {
  return (
    <div className="hero">
      <h1>Không tìm thấy sản phẩm</h1>
      <p className="muted">Sản phẩm không tồn tại hoặc đã ngừng bán.</p>
      <p>
        <Link to="/products" className="btn btn-primary">
          Xem các sản phẩm khác
        </Link>
      </p>
    </div>
  );
}

export default ProductNotFound;
