import { useEffect } from 'react';

/** Phần tử có hiệu ứng gợn sóng khi bấm. */
export const RIPPLE_SELECTOR = '.btn, .chip, .icon-link, .product-card, .category-menu-link';

/** Người dùng bật "giảm chuyển động" trong hệ điều hành thì không vẽ hiệu ứng. */
function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

/**
 * Vẽ 1 vòng gợn sóng tại điểm bấm trên target: vòng là span.ripple nằm trong target, tự xóa khi
 * hiệu ứng chạy xong (hoặc sau 1 giây nếu trình duyệt không báo animationend).
 */
export function spawnRipple(target, clientX, clientY) {
  const rect = target.getBoundingClientRect();
  const size = Math.max(rect.width, rect.height) * 2;
  const ripple = document.createElement('span');
  ripple.className = 'ripple';
  ripple.style.width = `${size}px`;
  ripple.style.height = `${size}px`;
  ripple.style.left = `${clientX - rect.left - size / 2}px`;
  ripple.style.top = `${clientY - rect.top - size / 2}px`;
  target.appendChild(ripple);
  const remove = () => ripple.remove();
  ripple.addEventListener('animationend', remove, { once: true });
  setTimeout(remove, 1000);
  return ripple;
}

/**
 * Bật hiệu ứng gợn sóng cho mọi nút, thẻ sản phẩm, liên kết menu trên trang: 1 bộ lắng nghe
 * pointerdown chung ở document thay vì gắn vào từng component.
 */
export function usePressRipple() {
  useEffect(() => {
    const handlePointerDown = (event) => {
      if (event.button !== 0 || prefersReducedMotion()) return;
      const target = event.target.closest?.(RIPPLE_SELECTOR);
      if (!target || target.disabled) return;
      spawnRipple(target, event.clientX, event.clientY);
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, []);
}
