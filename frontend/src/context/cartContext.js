import { createContext } from 'react';

// Tách riêng khỏi CartProvider.jsx: file component chỉ nên export component (react-refresh).
export const CartContext = createContext(null);
