import { Link } from 'react-router';

/**
 * Trang thanh toán (US-09, US-10). Bước này mới có đường dẫn và chặn đăng nhập (US-08); địa chỉ
 * giao hàng, phương thức thanh toán và xác nhận đơn được làm ở bước đặt hàng tiếp theo.
 */
function CheckoutPage() {
  return (
    <div className="empty-state">
      <h1>Thanh toán</h1>
      <p>Phần nhập địa chỉ giao hàng và xác nhận đơn hàng đang được hoàn thiện.</p>
      <Link to="/cart" className="btn btn-ghost">
        Quay lại giỏ hàng
      </Link>
    </div>
  );
}

export default CheckoutPage;
