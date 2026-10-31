import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { formatSecondsAgo } from '../services/format';
import { getMyRecommendations } from '../services/recommendationApi';
import { loadSeenProducts, saveSeenProducts } from '../services/recommendationSeen';
import Icon from './Icon';
import ProductImage from './ProductImage';
import RecommendationGrid from './RecommendationGrid';
import SurveyHome from './SurveyHome';

const SUBTITLES = {
  BEHAVIOR: 'Dựa trên sản phẩm bạn đã xem, đã mua và đang có trong giỏ hàng.',
  SURVEY: 'Dựa trên câu trả lời khảo sát của bạn.',
};

/** ml-service còn đang tính lại thì tải lại sau chừng này mili giây, tối đa MAX_POLLS lần. */
const POLL_MS = 2500;
const MAX_POLLS = 6;

/**
 * Khối gợi ý ở trang chủ (US-13):
 * - Hàng "Vì bạn vừa xem / vừa thêm vào giỏ ...": sản phẩm liên quan của hành động gần nhất, đổi
 *   ngay theo từng lần xem hay thêm giỏ.
 * - "Gợi ý cho bạn": danh sách ml-service tính sẵn (theo hành vi hoặc khảo sát) kèm lý do, giờ cập
 *   nhật và nhãn "Mới" cho sản phẩm chưa có ở lần khách thấy trước. Khi backend báo đang tính lại,
 *   khối tự tải lại sau vài giây để khách thấy danh sách mới mà không phải tải lại trang.
 * Chưa có danh sách riêng, chưa đăng nhập, hay tải lỗi thì hiện phần khảo sát thay danh sách.
 */
function PersonalRecommendations() {
  const { user } = useAuth();
  const accountId = user?.role === 'CUSTOMER' ? user.id : null;
  const { data, lastData, loading, reload } = useApiData(
    accountId === null ? null : String(accountId),
    getMyRecommendations
  );
  // Giữ danh sách cũ trên màn hình trong lúc tự tải lại.
  const shown = data ?? lastData;
  const [polls, setPolls] = useState(0);
  // Các sản phẩm khách thấy ở lần trước, đọc 1 lần cho mỗi tài khoản (trước khi effect bên dưới
  // ghi danh sách mới) để so sánh suốt lần xem trang này. Tài khoản có thể chỉ biết sau khi
  // AuthProvider tải xong, nên không đọc trong useState lúc mới vẽ.
  const seenBefore = useMemo(
    () => (accountId === null ? null : loadSeenProducts(accountId)),
    [accountId]
  );

  const personal = Boolean(shown && shown.source !== 'POPULAR' && shown.products.length > 0);
  const idsKey = personal ? shown.products.map((item) => item.product.id).join(',') : '';

  useEffect(() => {
    if (!shown?.updating || polls >= MAX_POLLS) return undefined;
    const timer = setTimeout(() => {
      setPolls((n) => n + 1);
      reload();
    }, POLL_MS);
    return () => clearTimeout(timer);
  }, [shown, polls, reload]);

  useEffect(() => {
    if (accountId !== null && idsKey) saveSeenProducts(accountId, idsKey.split(',').map(Number));
  }, [accountId, idsKey]);

  const newIds = useMemo(() => {
    if (!seenBefore || !idsKey) return new Set();
    const before = new Set(seenBefore);
    return new Set(
      idsKey
        .split(',')
        .map(Number)
        .filter((id) => !before.has(id))
    );
  }, [seenBefore, idsKey]);

  if (!shown) return loading ? null : <SurveyHome />;

  return (
    <>
      {shown.recent && <RecentActivity recent={shown.recent} />}
      {personal ? (
        <section className="product-section">
          <div className="section-header">
            <h2>Gợi ý cho bạn</h2>
            <Link to="/survey">Làm lại khảo sát ›</Link>
          </div>
          <p className="muted small recommendation-status">
            <span>{SUBTITLES[shown.source]}</span>
            {shown.updating && polls < MAX_POLLS ? (
              <span className="recommendation-updating" role="status">
                Đang cập nhật theo hoạt động mới của bạn...
              </span>
            ) : (
              shown.updatedSecondsAgo != null && (
                <span>Cập nhật {formatSecondsAgo(shown.updatedSecondsAgo)}</span>
              )
            )}
          </p>
          <RecommendationGrid items={shown.products} placement="HOME" newIds={newIds} />
        </section>
      ) : (
        <SurveyHome />
      )}
    </>
  );
}

const RECENT_KINDS = {
  VIEW: { title: 'Vì bạn vừa xem', icon: 'eye', again: 'Xem lại sản phẩm' },
  CART: { title: 'Vì bạn vừa thêm vào giỏ', icon: 'bag', again: 'Xem sản phẩm trong giỏ' },
};

/**
 * Hàng "Vì bạn vừa xem / vừa thêm vào giỏ <tên sản phẩm>" kèm sản phẩm liên quan. Đầu hàng là thẻ
 * nhỏ của sản phẩm vừa tương tác (ảnh, nhãn hành động, tên, danh mục), để khách thấy ngay gợi ý dựa
 * trên sản phẩm nào.
 */
function RecentActivity({ recent }) {
  const kind = RECENT_KINDS[recent.kind] ?? RECENT_KINDS.VIEW;
  const { product, products } = recent;
  const href = `/products/${product.id}`;
  const meta = [product.categoryName, product.brandName].filter(Boolean).join(' · ');
  return (
    <section className="product-section recent-activity">
      <div className="recent-anchor">
        <Link to={href} className="recent-anchor-image" tabIndex={-1} aria-hidden="true">
          <ProductImage
            src={product.thumbnailUrl}
            productId={product.id}
            categoryName={product.categoryName}
          />
        </Link>
        <div className="recent-anchor-text">
          <h2>
            <span className="recent-kind">
              <Icon name={kind.icon} size={16} />
              {kind.title}
            </span>{' '}
            <Link to={href} className="recent-anchor-name" title={product.name}>
              {product.name}
            </Link>
          </h2>
          <p className="muted small">
            {meta && `${meta} · `}
            {products.length} sản phẩm tương tự bạn có thể thích
          </p>
        </div>
        <Link to={href} className="recent-anchor-again small">
          {kind.again} ›
        </Link>
      </div>
      <RecommendationGrid items={products} placement="HOME_RECENT" />
    </section>
  );
}

export default PersonalRecommendations;
