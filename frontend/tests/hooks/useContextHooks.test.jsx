import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/context/authContext';
import { useAuth } from '../../src/hooks/useAuth';
import { useCart } from '../../src/hooks/useCart';

describe('useAuth, useCart', () => {
  it('báo lỗi khi dùng ngoài Provider', () => {
    // React ghi lỗi của component ra console, test chỉ cần biết hook ném lỗi.
    vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => renderHook(() => useAuth())).toThrow('useAuth phải dùng bên trong <AuthProvider>');
    expect(() => renderHook(() => useCart())).toThrow('useCart phải dùng bên trong <CartProvider>');
  });

  it('trả giá trị của Provider', () => {
    const value = { user: null };
    const { result } = renderHook(() => useAuth(), {
      wrapper: ({ children }) => (
        <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
      ),
    });
    expect(result.current).toBe(value);
  });
});
