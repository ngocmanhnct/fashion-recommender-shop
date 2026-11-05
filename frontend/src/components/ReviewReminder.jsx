import { Link } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { getPendingReviews } from '../services/reviewApi';
import Icon from './Icon';

/**
 * Dải nhắc ở trang chủ khi khách có sản phẩm đã nhận mà chưa đánh giá (US-15), dẫn tới mục "Chờ
 * đánh giá" của trang Đơn hàng của tôi. Chưa đăng nhập, không phải khách hàng hoặc không có sản
 * phẩm nào chờ thì không hiện.
 */
function ReviewReminder() {
  const { user } = useAuth();
  const isCustomer = user?.role === 'CUSTOMER';
  const { data } = useApiData(isCustomer ? `pending-reviews:${user.id}` : null, getPendingReviews);
  if (!data?.total) return null;
  const first = data.items[0];
  return (
    <Link to="/orders#cho-danh-gia" className="offers-banner review-reminder">
      <Icon name="star" size={24} />
      <span className="offers-banner-text">
        <strong>Bạn có {data.total} sản phẩm đã nhận chờ đánh giá</strong>
        {first && (
          <span className="small">
            {first.productName}
            {data.total > 1 && ` và ${data.total - 1} sản phẩm khác`}
          </span>
        )}
      </span>
      <span className="offers-banner-link">Đánh giá ngay ›</span>
    </Link>
  );
}

export default ReviewReminder;
