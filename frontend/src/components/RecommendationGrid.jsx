import { useEffect, useRef } from 'react';
import { useAuth } from '../hooks/useAuth';
import { logRecommendationEvent } from '../services/recommendationApi';
import ProductGrid from './ProductGrid';

/** Phần diện tích khối gợi ý phải lọt vào màn hình thì mới tính là một lượt hiện. */
const SHOWN_THRESHOLD = 0.3;

/** Sản phẩm gửi kèm sự kiện: mã, nguồn và thứ hạng (bắt đầu từ 1). */
function toEventItem(item, index) {
  return { productId: item.product.id, source: item.source, rank: index + 1 };
}

/** Ghi lượt hiện của cả khối, mỗi danh sách (key) một lần. */
function logShownOnce(loggedKey, key, placement, items) {
  if (loggedKey.current === key) return;
  loggedKey.current = key;
  logRecommendationEvent('SHOWN', placement, items.map(toEventItem));
}

/**
 * Lưới sản phẩm gợi ý kèm lý do, có ghi nhật ký để tính tỷ lệ bấm (CTR): một lượt hiện khi khối lọt
 * vào màn hình lần đầu, một lượt bấm khi bấm vào sản phẩm. Quản trị viên không được ghi.
 * items: [{ product, reason, source }] từ API gợi ý. placement: HOME, HOME_RECENT, PRODUCT_DETAIL,
 * SURVEY_RESULT. newIds: tập mã sản phẩm hiện nhãn "Mới", không bắt buộc.
 */
function RecommendationGrid({ items, placement, newIds }) {
  const { user } = useAuth();
  const tracked = user?.role !== 'ADMIN';
  const containerRef = useRef(null);
  const loggedKey = useRef('');
  const key = `${placement}:${items.map((item) => item.product.id).join(',')}`;

  useEffect(() => {
    if (!tracked || items.length === 0 || loggedKey.current === key) return undefined;
    const node = containerRef.current;
    if (!node || typeof IntersectionObserver === 'undefined') {
      logShownOnce(loggedKey, key, placement, items);
      return undefined;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          observer.disconnect();
          logShownOnce(loggedKey, key, placement, items);
        }
      },
      { threshold: SHOWN_THRESHOLD }
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [items, key, placement, tracked]);

  // Bấm được thì đã thấy: ghi lượt hiện trước nếu khối chưa kịp ghi (vd bấm ngay khi vừa cuộn tới).
  const handleClick = (product, index) => {
    if (!tracked) return;
    logShownOnce(loggedKey, key, placement, items);
    logRecommendationEvent('CLICKED', placement, [toEventItem(items[index], index)]);
  };

  const reasons = Object.fromEntries(items.map((item) => [item.product.id, item.reason]));
  return (
    <div ref={containerRef}>
      <ProductGrid
        products={items.map((item) => item.product)}
        reasons={reasons}
        onProductClick={handleClick}
        newIds={newIds}
      />
    </div>
  );
}

export default RecommendationGrid;
