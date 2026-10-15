// Lưu JWT trong localStorage để tải lại trang vẫn còn đăng nhập. Token sống 24 giờ (NFR-03);
// lưu kèm thời điểm hết hạn để tự bỏ token cũ thay vì gửi lên rồi mới bị 401.
const TOKEN_KEY = 'fashionshop.token';
const EXPIRES_AT_KEY = 'fashionshop.tokenExpiresAt';

export function saveToken(token, expiresInSeconds) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(EXPIRES_AT_KEY, String(Date.now() + expiresInSeconds * 1000));
}

export function getToken() {
  const token = localStorage.getItem(TOKEN_KEY);
  const expiresAt = Number(localStorage.getItem(EXPIRES_AT_KEY));
  if (!token || !expiresAt || Date.now() >= expiresAt) {
    clearToken();
    return null;
  }
  return token;
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(EXPIRES_AT_KEY);
}
