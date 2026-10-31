import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../services/errorMessage';
import { formatNumber, formatRating } from '../services/format';
import { formatDateTime } from '../services/orderLabels';
import { getMyReview, getProductReviews } from '../services/reviewApi';
import ReviewForm from './ReviewForm';

const PAGE_SIZE = 5;

/**
 * Phần "Đánh giá sản phẩm" ở trang chi tiết (US-15): điểm trung bình, số đánh giá theo mức sao,
 * danh sách đánh giá (sửa gần nhất trước, bấm "Xem thêm" để tải tiếp) và ô đánh giá cho khách hàng
 * đã nhận sản phẩm (BR-17). Mở trang với #danh-gia (từ đơn hàng Đã giao) thì cuộn tới phần này.
 */
function ProductReviews({ productId }) {
  const { user } = useAuth();
  const location = useLocation();
  const sectionRef = useRef(null);
  // Tăng sau khi gửi đánh giá để tải lại điểm và danh sách.
  const [version, setVersion] = useState(0);
  const [pages, setPages] = useState(1);
  const key = `${productId}:${version}:${pages}`;
  const { data, error, lastData, loading } = useApiData(key, () =>
    getProductReviews(productId, 0, PAGE_SIZE * pages)
  );
  const shown = data ?? lastData;

  useEffect(() => {
    if (location.hash === '#danh-gia' && shown) sectionRef.current?.scrollIntoView?.();
  }, [location.hash, shown]);

  const handleSaved = () => {
    setPages(1);
    setVersion((v) => v + 1);
  };

  return (
    <section className="product-section product-reviews" id="danh-gia" ref={sectionRef}>
      <h2>Đánh giá sản phẩm</h2>
      {user?.role === 'CUSTOMER' && (
        <MyReview key={`${user.id}:${productId}`} productId={productId} onSaved={handleSaved} />
      )}
      {error && !shown && (
        <p className="muted small">{getErrorMessage(error, 'Không tải được đánh giá')}</p>
      )}
      {!shown && !error && <p className="muted small">Đang tải đánh giá...</p>}
      {shown && <ReviewSummary summary={shown} />}
      {shown && shown.reviews.content.length > 0 && (
        <>
          <ul className="review-list">
            {shown.reviews.content.map((review) => (
              <ReviewItem key={review.id} review={review} />
            ))}
          </ul>
          {shown.reviews.totalElements > shown.reviews.content.length && (
            <button
              type="button"
              className="btn btn-ghost"
              disabled={loading}
              onClick={() => setPages((n) => n + 1)}
            >
              {loading ? 'Đang tải...' : 'Xem thêm đánh giá'}
            </button>
          )}
        </>
      )}
    </section>
  );
}

function MyReview({ productId, onSaved }) {
  const { data } = useApiData(productId, getMyReview);
  if (!data) return null;
  if (!data.canReview) {
    return <p className="muted small">Bạn có thể đánh giá sau khi nhận được sản phẩm này.</p>;
  }
  return <ReviewForm productId={productId} review={data.review} onSaved={onSaved} />;
}

function ReviewSummary({ summary }) {
  if (summary.reviewCount === 0) {
    return <p className="muted">Chưa có đánh giá nào.</p>;
  }
  return (
    <div className="review-summary">
      <p className="review-average">
        <strong>{formatRating(summary.ratingAverage)}</strong>/5
        <span className="muted small"> · {formatNumber(summary.reviewCount)} đánh giá</span>
      </p>
      <ul className="review-bars">
        {[5, 4, 3, 2, 1].map((star) => {
          const count = summary.starCounts[star - 1];
          const percent = Math.round((count * 100) / summary.reviewCount);
          return (
            <li key={star}>
              <span>{star} sao</span>
              <span className="review-bar" aria-hidden="true">
                <span style={{ width: `${percent}%` }} />
              </span>
              <span className="muted small">{formatNumber(count)}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function ReviewItem({ review }) {
  return (
    <li className="review-item">
      <p>
        <strong>{review.reviewerName}</strong>{' '}
        <span className="review-stars" aria-label={`${review.rating} trên 5 sao`}>
          {'★'.repeat(review.rating)}
          {'☆'.repeat(5 - review.rating)}
        </span>
      </p>
      <p className="muted small">
        {formatDateTime(review.updatedAt)}
        {review.variantLabel && ` · Phân loại: ${review.variantLabel}`}
      </p>
      {review.comment && <p className="review-comment">{review.comment}</p>}
    </li>
  );
}

export default ProductReviews;
