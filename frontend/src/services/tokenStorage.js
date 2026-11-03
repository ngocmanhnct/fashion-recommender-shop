// Lưu JWT trong localStorage để tải lại trang vẫn còn đăng nhập. Token sống 24 giờ (NFR-03);
// lưu kèm thời điểm hết hạn để tự bỏ token cũ thay vì gửi lên rồi mới bị 401.
export const TOKEN_KEY = 'fashionshop.token';
const EXPIRES_AT_KEY = 'fashionshop.tokenExpiresAt';

// Sự kiện báo phiên đăng nhập đã hết: backend trả 401 cho request có gửi token, hoặc token lưu
// trong máy vừa quá hạn. AuthProvider nghe sự kiện này để đăng xuất.
export const UNAUTHORIZED_EVENT = 'fashionshop:unauthorized';

export function saveToken(token, expiresInSeconds) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(EXPIRES_AT_KEY, String(Date.now() + expiresInSeconds * 1000));
}

export function getToken() {
  const token = localStorage.getItem(TOKEN_KEY);
  const expiresAt = Number(localStorage.getItem(EXPIRES_AT_KEY));
  if (!token || !expiresAt || Date.now() >= expiresAt) {
    clearToken();
    // Token vừa quá hạn (không phải chưa đăng nhập): báo để giao diện đăng xuất, nếu không trang
    // vẫn hiện "Xin chào" trong khi mọi API cần đăng nhập đều trả 401.
    if (token) window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    return null;
  }
  return token;
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(EXPIRES_AT_KEY);
}
