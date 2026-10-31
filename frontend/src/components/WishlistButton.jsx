import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../services/errorMessage';
import { addToWishlist, getWishlistIds, removeFromWishlist } from '../services/wishlistApi';

/**
 * Nút "Yêu thích" ở trang chi tiết sản phẩm (US-14). Khách hàng bấm để thêm hoặc bỏ sản phẩm khỏi
 * danh sách yêu thích; khách chưa đăng nhập bấm thì sang trang đăng nhập rồi quay lại sản phẩm;
 * Quản trị viên không thấy nút.
 */
function WishlistButton({ productId }) {
  const { user, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isCustomer = user?.role === 'CUSTOMER';
  const { data } = useApiData(isCustomer ? `wishlist:${user.id}` : null, getWishlistIds);
  // Danh sách sau lần bấm gần nhất; null = dùng danh sách vừa tải.
  const [ids, setIds] = useState(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  if (authLoading || user?.role === 'ADMIN') return null;

  const liked = (ids ?? data ?? []).includes(productId);

  const toggle = async () => {
    if (!isCustomer) {
      navigate('/login', { state: { from: location.pathname } });
      return;
    }
    setPending(true);
    setError('');
    try {
      setIds(await (liked ? removeFromWishlist(productId) : addToWishlist(productId)));
    } catch (err) {
      setError(getErrorMessage(err, 'Không cập nhật được danh sách yêu thích'));
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="wishlist-action">
      <button
        type="button"
        className={`btn btn-ghost wishlist-btn${liked ? ' is-liked' : ''}`}
        aria-pressed={liked}
        disabled={pending || (isCustomer && data === null && ids === null)}
        onClick={toggle}
      >
        <span aria-hidden="true">{liked ? '♥' : '♡'} </span>
        {liked ? 'Đã yêu thích' : 'Yêu thích'}
      </button>
      {error && (
        <p className="alert alert-error small" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

export default WishlistButton;
