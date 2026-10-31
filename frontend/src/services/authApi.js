import apiClient from './apiClient';

export function register(payload) {
  return apiClient.post('/api/auth/register', payload).then((res) => res.data);
}

export function login(email, password) {
  return apiClient.post('/api/auth/login', { email, password }).then((res) => res.data);
}

export function getMe() {
  return apiClient.get('/api/auth/me').then((res) => res.data);
}

/** Sửa hồ sơ của tài khoản đang đăng nhập: { fullName, gender, birthdate }; trả về tài khoản. */
export function updateProfile(profile) {
  return apiClient.put('/api/auth/me', profile).then((res) => res.data);
}

/** Đổi mật khẩu khi biết mật khẩu hiện tại. */
export function changePassword(currentPassword, newPassword) {
  return apiClient
    .put('/api/auth/me/password', { currentPassword, newPassword })
    .then((res) => res.data);
}

/** Xin liên kết đặt lại mật khẩu qua email; backend trả 202 dù email có tài khoản hay không. */
export function requestPasswordReset(email) {
  return apiClient.post('/api/auth/forgot-password', { email }).then((res) => res.data);
}

/** Đặt mật khẩu mới bằng mã trong liên kết của email. */
export function resetPassword(token, newPassword) {
  return apiClient.post('/api/auth/reset-password', { token, newPassword }).then((res) => res.data);
}
