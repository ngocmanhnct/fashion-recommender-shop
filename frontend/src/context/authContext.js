import { createContext } from 'react';

// Tách riêng khỏi AuthProvider.jsx: file component chỉ nên export component (react-refresh).
export const AuthContext = createContext(null);
