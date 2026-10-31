import { Link } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { formatDateTime } from '../services/orderLabels';
import { getMyVouchers } from '../services/voucherApi';
import { describeDiscount } from '../services/voucherLabels';
import Icon from './Icon';

/**
 * Dải báo ở trang chủ khi khách có mã giảm giá được tặng riêng (US-24, US-26): số mã, mã sắp hết
 * hạn nhất và liên kết tới "Ưu đãi của bạn". Khách chưa có mã hoặc chưa đăng nhập thì không hiện.
 */
function OffersBanner() {
  const { user } = useAuth();
  const isCustomer = user?.role === 'CUSTOMER';
  const { data } = useApiData(isCustomer ? `my-vouchers:${user.id}` : null, getMyVouchers);
  if (!data?.length) return null;
  const soonest = data[0];
  return (
    <Link to="/offers" className="offers-banner">
      <Icon name="ticket" size={24} />
      <span className="offers-banner-text">
        <strong>Bạn có {data.length} mã giảm giá được tặng riêng</strong>
        <span className="small">
          {soonest.code}: {describeDiscount(soonest)}, dùng đến {formatDateTime(soonest.validUntil)}
        </span>
      </span>
      <span className="offers-banner-link">Xem ưu đãi ›</span>
    </Link>
  );
}

export default OffersBanner;
