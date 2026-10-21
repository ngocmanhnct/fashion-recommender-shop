import { useParams } from 'react-router';
import ProductDetail from '../components/ProductDetail';
import ProductNotFound from '../components/ProductNotFound';

// Mã sản phẩm hợp lệ: tối đa 18 chữ số (luôn vừa kiểu Long của backend).
const PRODUCT_ID_PATTERN = /^\d{1,18}$/;

/**
 * Trang chi tiết sản phẩm /products/:id. Mã không hợp lệ (vd "abc") thì báo không tìm thấy luôn,
 * không gọi API. Mỗi mã sản phẩm là 1 ProductDetail mới (key): sang sản phẩm khác trên cùng
 * route thì trạng thái cũ (dữ liệu, lựa chọn phân loại) bị bỏ hẳn.
 */
function ProductDetailPage() {
  const { id } = useParams();
  if (!PRODUCT_ID_PATTERN.test(id)) return <ProductNotFound />;
  return <ProductDetail key={id} id={id} />;
}

export default ProductDetailPage;
