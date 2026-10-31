/**
 * Nhớ các sản phẩm của khối "Gợi ý cho bạn" lần trước khách thấy (theo tài khoản, trong
 * localStorage), để đánh dấu "Mới" các sản phẩm vừa xuất hiện sau khi gợi ý được tính lại.
 * localStorage có thể bị chặn (chế độ riêng tư): khi đó coi như chưa thấy gì và không đánh dấu.
 */
const KEY_PREFIX = 'goi-y-da-thay:';

/** Mã các sản phẩm đã thấy lần trước, null nếu chưa có (lần đầu hoặc không đọc được). */
export function loadSeenProducts(accountId) {
  try {
    const raw = localStorage.getItem(KEY_PREFIX + accountId);
    const ids = raw ? JSON.parse(raw) : null;
    return Array.isArray(ids) ? ids : null;
  } catch {
    return null;
  }
}

/** Lưu mã các sản phẩm đang hiện để lần sau so sánh. */
export function saveSeenProducts(accountId, ids) {
  try {
    localStorage.setItem(KEY_PREFIX + accountId, JSON.stringify(ids));
  } catch {
    // Không lưu được thì lần sau không có nhãn "Mới", không ảnh hưởng gì khác.
  }
}
