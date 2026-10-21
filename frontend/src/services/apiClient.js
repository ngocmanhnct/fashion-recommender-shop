import axios from 'axios';
import { clearToken, getToken } from './tokenStorage';

// Sự kiện phát ra khi backend trả 401 cho một request có gửi token: token đã hết hạn hoặc
// backend khởi động lại với khóa khác. AuthProvider nghe sự kiện này để đăng xuất.
export const UNAUTHORIZED_EVENT = 'fashionshop:unauthorized';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
});

// Đăng nhập, đăng ký không cần token. Token cũ còn lưu mà sai (vd backend đổi khóa) nếu gửi kèm
// thì backend trả 401 trước khi kiểm tra mật khẩu: nhập đúng vẫn bị báo đăng nhập không thành công.
const ANONYMOUS_URLS = ['/api/auth/login', '/api/auth/register'];

// Chỉ gắn token cho API nghiệp vụ (/api/...). Endpoint Actuator (/actuator/health) không cần
// đăng nhập, và CORS của Actuator không cho header Authorization: gửi kèm token thì trình duyệt
// chặn request, trang báo "Network Error". Lần gửi lại sau 401 (bên dưới) cũng không gắn token.
apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (
    token &&
    config.url?.startsWith('/api/') &&
    !ANONYMOUS_URLS.includes(config.url) &&
    !config._retriedWithoutToken
  ) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const config = error.config;
    const sentToken = Boolean(config?.headers?.Authorization);
    if (error.response?.status === 401 && sentToken) {
      clearToken();
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
      // API công khai (vd xem sản phẩm) vẫn trả 401 khi token gửi kèm đã hết hạn hoặc sai khóa:
      // gửi lại đúng 1 lần, không kèm token. Chỉ gửi lại GET; không bao giờ gửi lại POST/PUT/DELETE
      // (thao tác ghi cần đăng nhập, gửi lại không token chỉ nhận thêm 1 lỗi 401).
      if ((config.method ?? 'get') === 'get' && !config._retriedWithoutToken) {
        config._retriedWithoutToken = true;
        config.headers.delete('Authorization');
        return apiClient(config);
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
