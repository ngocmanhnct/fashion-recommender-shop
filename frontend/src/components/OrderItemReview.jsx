import { useState } from 'react';
import { useApiData } from '../hooks/useApiData';
import { getMyReview } from '../services/reviewApi';
import ReviewForm from './ReviewForm';

/**
 * Ô đánh giá dưới 1 dòng của đơn Đã giao (US-15): bấm là chấm sao ngay trong trang đơn hàng. pending
 * = sản phẩm chưa được đánh giá (nút "Đánh giá ngay"), ngược lại là "Sửa đánh giá" và ô hiện sẵn
 * đánh giá cũ (BR-18). onSaved gọi sau khi lưu.
 */
function OrderItemReview({ productId, pending, onSaved }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="order-item-review">
      <button
        type="button"
        className={pending && !open ? 'btn btn-primary btn-sm' : 'btn btn-ghost btn-sm'}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        {open ? 'Đóng' : pending ? 'Đánh giá ngay' : 'Sửa đánh giá'}
      </button>
      {open && <OpenReview productId={productId} onSaved={onSaved} />}
    </div>
  );
}

function OpenReview({ productId, onSaved }) {
  const { data, error } = useApiData(productId, getMyReview);
  if (error) return <p className="muted small">Không tải được đánh giá, hãy thử lại sau.</p>;
  if (!data) return <p className="muted small">Đang tải...</p>;
  return <ReviewForm productId={productId} review={data.review} onSaved={onSaved} />;
}

export default OrderItemReview;
