import { useState } from 'react';
import { getErrorMessage, getFieldErrors } from '../services/errorMessage';
import { saveMyReview } from '../services/reviewApi';

const MAX_COMMENT = 1000;

/**
 * Ô đánh giá của khách hàng đã nhận sản phẩm (US-15): chọn 1 đến 5 sao và viết nhận xét. Đã đánh
 * giá thì hiện sẵn đánh giá cũ, gửi lại là sửa (BR-18). onSaved gọi sau khi lưu xong.
 */
function ReviewForm({ productId, review, onSaved }) {
  const [rating, setRating] = useState(review?.rating ?? 0);
  const [comment, setComment] = useState(review?.comment ?? '');
  const [hasReview, setHasReview] = useState(review != null);
  const [status, setStatus] = useState({ type: 'idle', message: '' });

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (rating === 0) {
      setStatus({ type: 'error', message: 'Hãy chọn số sao' });
      return;
    }
    setStatus({ type: 'saving', message: '' });
    try {
      await saveMyReview(productId, { rating, comment });
      setHasReview(true);
      setStatus({ type: 'saved', message: 'Đã lưu đánh giá của bạn.' });
      onSaved();
    } catch (err) {
      const fieldErrors = getFieldErrors(err);
      const message =
        fieldErrors.rating ||
        fieldErrors.comment ||
        getErrorMessage(err, 'Không lưu được đánh giá');
      setStatus({ type: 'error', message });
    }
  };

  return (
    <form className="review-form" onSubmit={handleSubmit}>
      <fieldset className="review-rating">
        <legend>{hasReview ? 'Đánh giá của bạn' : 'Đánh giá sản phẩm bạn đã mua'}</legend>
        {[1, 2, 3, 4, 5].map((star) => (
          <label key={star} className={star <= rating ? 'is-on' : undefined}>
            <input
              type="radio"
              name="rating"
              value={star}
              checked={rating === star}
              onChange={() => setRating(star)}
              aria-label={`${star} sao`}
            />
            <span aria-hidden="true">★</span>
          </label>
        ))}
      </fieldset>
      <label className="field">
        <span className="field-label">Nhận xét (không bắt buộc)</span>
        <textarea
          value={comment}
          maxLength={MAX_COMMENT}
          rows={3}
          onChange={(event) => setComment(event.target.value)}
        />
      </label>
      <button type="submit" className="btn btn-primary" disabled={status.type === 'saving'}>
        {hasReview ? 'Cập nhật đánh giá' : 'Gửi đánh giá'}
      </button>
      {status.type === 'saved' && (
        <p className="alert alert-success small" role="status">
          {status.message}
        </p>
      )}
      {status.type === 'error' && (
        <p className="alert alert-error small" role="alert">
          {status.message}
        </p>
      )}
    </form>
  );
}

export default ReviewForm;
