import { useCallback, useEffect, useMemo, useState } from 'react';
import * as authApi from '../services/authApi';
import { UNAUTHORIZED_EVENT } from '../services/apiClient';
import { clearToken, getToken, saveToken, TOKEN_KEY } from '../services/tokenStorage';
import { AuthContext } from './authContext';

function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  // Có token cũ thì phải hỏi /me trước khi biết là ai, trong lúc đó chưa vẽ trang cần đăng nhập.
  const [loading, setLoading] = useState(() => getToken() !== null);

  useEffect(() => {
    if (!getToken()) return;
    authApi
      .getMe()
      .then(setUser)
      // Token sai hoặc tài khoản không còn thì xóa token. Lỗi khác (mất mạng, backend đang khởi
      // động) thì giữ token: tải lại trang là vào lại được, không bắt đăng nhập lại.
      .catch((error) => {
        if ([401, 403, 404].includes(error?.response?.status)) clearToken();
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => setUser(null);
    // Đăng xuất ở tab khác thì localStorage mất token: tab này cũng đăng xuất theo.
    const handleStorage = (event) => {
      if ((event.key === TOKEN_KEY || event.key === null) && !getToken()) setUser(null);
    };
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
    window.addEventListener('storage', handleStorage);
    return () => {
      window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
      window.removeEventListener('storage', handleStorage);
    };
  }, []);

  const login = useCallback(async (email, password) => {
    const auth = await authApi.login(email, password);
    saveToken(auth.accessToken, auth.expiresIn);
    setUser(auth.account);
    return auth.account;
  }, []);

  const register = useCallback(async (payload) => {
    const auth = await authApi.register(payload);
    saveToken(auth.accessToken, auth.expiresIn);
    setUser(auth.account);
    return auth.account;
  }, []);

  // Sửa hồ sơ xong: thay thông tin tài khoản đang hiện (vd tên ở "Xin chào, ..."), token giữ nguyên.
  const updateUser = useCallback((account) => setUser(account), []);

  const logout = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, logout, updateUser }),
    [user, loading, login, register, logout, updateUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export default AuthProvider;
