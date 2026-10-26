import { Link } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { getMyRecommendations } from '../services/recommendationApi';
import ProductGrid from './ProductGrid';
import SurveyHome from './SurveyHome';

const SUBTITLES = {
  BEHAVIOR: 'Dựa trên sản phẩm bạn đã xem, đã mua và đang có trong giỏ hàng.',
  SURVEY: 'Dựa trên câu trả lời khảo sát của bạn.',
};

/**
 * Khối "Gợi ý cho bạn" ở trang chủ (US-13): khách hàng đã có danh sách gợi ý riêng (theo hành vi
 * hoặc theo khảo sát, ml-service tính sẵn) thì hiện danh sách đó kèm lý do. Chưa có danh sách,
 * chưa đăng nhập, hay tải lỗi thì hiện phần khảo sát (mời làm khảo sát hoặc gợi ý theo câu trả lời).
 */
function PersonalRecommendations() {
  const { user } = useAuth();
  const requestKey = user?.role === 'CUSTOMER' ? String(user.id) : null;
  const { data, loading } = useApiData(requestKey, getMyRecommendations);

  if (loading) return null;
  if (!data || data.source === 'POPULAR' || data.products.length === 0) return <SurveyHome />;

  const reasons = Object.fromEntries(data.products.map((item) => [item.product.id, item.reason]));
  return (
    <section className="product-section">
      <div className="section-header">
        <h2>Gợi ý cho bạn</h2>
        <Link to="/survey">Làm lại khảo sát ›</Link>
      </div>
      <p className="muted small">{SUBTITLES[data.source]}</p>
      <ProductGrid products={data.products.map((item) => item.product)} reasons={reasons} />
    </section>
  );
}

export default PersonalRecommendations;
