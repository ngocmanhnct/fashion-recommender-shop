import { useCallback, useEffect, useMemo, useState } from 'react';
import * as authApi from '../services/authApi';
import { UNAUTHORIZED_EVENT } from '../services/apiClient';
import { clearToken, getToken, saveToken } from '../services/tokenStorage';
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
      .catch(() => clearToken())
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => setUser(null);
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
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

  const logout = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, logout }),
    [user, loading, login, register, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export default AuthProvider;
