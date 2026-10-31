import { useEffect, useRef, useState } from 'react';

/**
 * Khối hiện dần (mờ sang rõ, trượt nhẹ lên) khi lần đầu cuộn tới. Trình duyệt không có
 * IntersectionObserver (vd jsdom khi test) thì hiện ngay. as: thẻ HTML bao ngoài (mặc định div).
 */
function Reveal({ as: Tag = 'div', className = '', children, ...rest }) {
  const ref = useRef(null);
  const [visible, setVisible] = useState(() => typeof IntersectionObserver === 'undefined');

  useEffect(() => {
    if (visible) return undefined;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: '0px 0px -10% 0px' }
    );
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [visible]);

  return (
    <Tag ref={ref} className={`reveal${visible ? ' is-visible' : ''} ${className}`} {...rest}>
      {children}
    </Tag>
  );
}

export default Reveal;
