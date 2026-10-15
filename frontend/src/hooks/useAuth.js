import { useContext } from 'react';
import { AuthContext } from '../context/authContext';

/** Trả về { user, loading, login, register, logout }. user = null khi chưa đăng nhập. */
export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth phải dùng bên trong <AuthProvider>');
  }
  return value;
}
