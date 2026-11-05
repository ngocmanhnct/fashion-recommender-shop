import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { formatDateTime } from '../services/orderLabels';
import { getPendingReviews } from '../services/reviewApi';
import ProductImage from './ProductImage';
import ReviewForm from './ReviewForm';

/**
 * Mục "Chờ đánh giá" ở trang Đơn hàng của tôi (US-15): các sản phẩm đã nhận mà chưa đánh giá, bấm
 * "Đánh giá" là chấm sao ngay tại chỗ, không phải mở lại trang sản phẩm. Sản phẩm vừa đánh giá hiện
 * dấu "Đã đánh giá" đến lần tải trang sau. Không có sản phẩm nào chờ thì không hiện. Mở trang với
 * #cho-danh-gia (dải nhắc ở trang chủ) thì cuộn tới mục này.
 */
function PendingReviews() {
  const { user } = useAuth();
  const { data } = useApiData(`pending-reviews:${user.id}`, getPendingReviews);
  const [openId, setOpenId] = useState(null);
  const [doneIds, setDoneIds] = useState([]);
  const location = useLocation();
  const sectionRef = useRef(null);
  const scrolledRef = useRef(false);
  const hasItems = Boolean(data?.items.length);

  useEffect(() => {
    if (location.hash !== '#cho-danh-gia' || !hasItems || scrolledRef.current) return;
    scrolledRef.current = true;
    sectionRef.current?.scrollIntoView?.({ block: 'start' });
  }, [location.hash, hasItems]);

  if (!hasItems) return null;
  const remaining = data.total - doneIds.length;

  return (
    <section className="pending-reviews" id="cho-danh-gia" ref={sectionRef}>
      <h2>Chờ đánh giá {remaining > 0 && <span className="muted">({remaining})</span>}</h2>
      <p className="muted small">
        Sản phẩm bạn đã nhận. Chấm sao ngay ở đây để giúp người mua sau chọn đúng.
      </p>
      <ul className="pending-review-list">
        {data.items.map((item) => {
          const done = doneIds.includes(item.productId);
          const open = openId === item.productId;
          return (
            <li key={item.productId} className="pending-review">
              <div className="pending-review-head">
                <ProductImage
                  productId={item.productId}
                  categoryName={item.categoryName}
                  className="pending-review-image"
                />
                <div className="pending-review-info">
                  <Link to={`/products/${item.productId}`}>{item.productName}</Link>
                  <p className="muted small">
                    {item.variantLabel && `${item.variantLabel} · `}
                    <Link to={`/orders/${item.orderId}`}>Đơn #{item.orderId}</Link> ·{' '}
                    {formatDateTime(item.orderedAt)}
                  </p>
                </div>
                {done ? (
                  <span className="pending-review-done small">✓ Đã đánh giá</span>
                ) : (
                  <button
                    type="button"
                    className={open ? 'btn btn-ghost btn-sm' : 'btn btn-primary btn-sm'}
                    aria-expanded={open}
                    onClick={() => setOpenId(open ? null : item.productId)}
                  >
                    {open ? 'Đóng' : 'Đánh giá'}
                  </button>
                )}
              </div>
              {open && !done && (
                <ReviewForm
                  productId={item.productId}
                  review={null}
                  onSaved={() => {
                    setDoneIds((ids) => [...ids, item.productId]);
                    setOpenId(null);
                  }}
                />
              )}
            </li>
          );
        })}
      </ul>
      {data.total > data.items.length && (
        <p className="muted small">
          Còn {data.total - data.items.length} sản phẩm khác, hiện sau khi bạn đánh giá các sản phẩm
          trên.
        </p>
      )}
    </section>
  );
}

export default PendingReviews;
