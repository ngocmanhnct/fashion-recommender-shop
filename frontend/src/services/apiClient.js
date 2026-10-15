import axios from 'axios';
import { clearToken, getToken } from './tokenStorage';

// Sự kiện phát ra khi backend trả 401 cho một request có gửi token: token đã hết hạn hoặc
// backend khởi động lại với khóa khác. AuthProvider nghe sự kiện này để đăng xuất.
export const UNAUTHORIZED_EVENT = 'fashionshop:unauthorized';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
});

// Chỉ gắn token cho API nghiệp vụ (/api/...). Endpoint Actuator (/actuator/health) không cần
// đăng nhập, và CORS của Actuator không cho header Authorization: gửi kèm token thì trình duyệt
// chặn request, trang báo "Network Error".
apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token && config.url?.startsWith('/api/')) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const sentToken = Boolean(error.config?.headers?.Authorization);
    if (error.response?.status === 401 && sentToken) {
      clearToken();
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    return Promise.reject(error);
  }
);

export default apiClient;
