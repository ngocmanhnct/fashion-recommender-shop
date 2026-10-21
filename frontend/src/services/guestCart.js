// Giỏ hàng của khách chưa đăng nhập lưu trong localStorage của trình duyệt (BR-06): chỉ lưu mã
// biến thể và số lượng; tên, giá, tồn kho luôn lấy mới từ backend (/api/cart/preview). Xóa dữ liệu
// trình duyệt thì mất giỏ hàng, đúng như đặc tả chấp nhận. Dữ liệu đọc ra được kiểm tra lại vì có
// thể bị sửa tay; localStorage không dùng được (vd chế độ riêng tư chặn) thì coi như giỏ rỗng.

const STORAGE_KEY = 'fashionshop.guestCart';

/** Số lượng tối đa mỗi dòng và số dòng tối đa, giống backend (CartService). */
export const MAX_QUANTITY = 99;
export const MAX_LINES = 50;

function isValidLine(line) {
  return (
    line !== null &&
    typeof line === 'object' &&
    Number.isSafeInteger(line.variantId) &&
    line.variantId > 0 &&
    Number.isInteger(line.quantity) &&
    line.quantity >= 1 &&
    line.quantity <= MAX_QUANTITY
  );
}

/** Các dòng [{ variantId, quantity }] đang lưu, bỏ dòng sai định dạng. */
export function readGuestCart() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter(isValidLine)
      .slice(0, MAX_LINES)
      .map(({ variantId, quantity }) => ({ variantId, quantity }));
  } catch {
    return [];
  }
}

/** Ghi đè toàn bộ giỏ hàng của trình duyệt; mảng rỗng thì xóa hẳn. */
export function writeGuestCart(lines) {
  try {
    if (lines.length === 0) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(lines));
    }
  } catch {
    /* localStorage không dùng được: giỏ hàng chỉ sống trong phiên này. */
  }
}

export function clearGuestCart() {
  writeGuestCart([]);
}

// Lỗi cùng dạng lỗi của axios để getErrorMessage đọc được như lỗi từ backend.
function cartError(detail) {
  const error = new Error(detail);
  error.response = { status: 409, data: { detail } };
  return error;
}

/** Thêm biến thể vào giỏ của trình duyệt, cộng dồn nếu đã có (tối đa 99 mỗi dòng, 50 dòng). */
export function addGuestLine(variantId, quantity) {
  const lines = readGuestCart();
  const existing = lines.find((line) => line.variantId === variantId);
  if (existing) {
    existing.quantity = Math.min(existing.quantity + quantity, MAX_QUANTITY);
  } else {
    if (lines.length >= MAX_LINES) {
      throw cartError(
        `Giỏ hàng tối đa ${MAX_LINES} sản phẩm khác nhau, hãy xóa bớt trước khi thêm`
      );
    }
    lines.push({ variantId, quantity });
  }
  writeGuestCart(lines);
}

/** Đổi số lượng của một dòng (1 đến 99). */
export function setGuestLine(variantId, quantity) {
  writeGuestCart(
    readGuestCart().map((line) => (line.variantId === variantId ? { variantId, quantity } : line))
  );
}

export function removeGuestLine(variantId) {
  writeGuestCart(readGuestCart().filter((line) => line.variantId !== variantId));
}

/** Lưu lại đúng các dòng backend trả về sau khi kiểm tra (đã bỏ dòng hết hàng, giảm số lượng). */
export function saveCheckedCart(cart) {
  writeGuestCart(cart.items.map(({ variantId, quantity }) => ({ variantId, quantity })));
}
