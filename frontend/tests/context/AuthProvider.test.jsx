import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AuthProvider from '../../src/context/AuthProvider';
import { useAuth } from '../../src/hooks/useAuth';
import * as authApi from '../../src/services/authApi';
import { UNAUTHORIZED_EVENT } from '../../src/services/apiClient';
import { getToken, saveToken } from '../../src/services/tokenStorage';

vi.mock('../../src/services/authApi');

const ACCOUNT = { id: 7, email: 'khach@example.com', role: 'CUSTOMER' };
const AUTH = { accessToken: 'token-moi', expiresIn: 3600, account: ACCOUNT };

function renderAuth() {
  return renderHook(() => useAuth(), { wrapper: AuthProvider });
}

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.mocked(authApi.getMe).mockResolvedValue(ACCOUNT);
  });

  it('không có token thì chưa đăng nhập, không hỏi /me', () => {
    const { result } = renderAuth();
    expect(result.current).toMatchObject({ user: null, loading: false });
    expect(authApi.getMe).not.toHaveBeenCalled();
  });

  it('có token cũ thì hỏi /me trước khi biết là ai', async () => {
    saveToken('token-cu', 3600);
    const { result } = renderAuth();
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.user).toEqual(ACCOUNT);
  });

  it('token cũ không dùng được thì xóa', async () => {
    saveToken('token-cu', 3600);
    vi.mocked(authApi.getMe).mockRejectedValue(new Error('401'));
    const { result } = renderAuth();
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.user).toBeNull();
    expect(getToken()).toBeNull();
  });

  it('đăng nhập, đăng ký lưu token; đăng xuất xóa token', async () => {
    vi.mocked(authApi.login).mockResolvedValue(AUTH);
    vi.mocked(authApi.register).mockResolvedValue({ ...AUTH, accessToken: 'token-dang-ky' });
    const { result } = renderAuth();

    await act(() => result.current.login('khach@example.com', 'mat-khau'));
    expect(authApi.login).toHaveBeenCalledWith('khach@example.com', 'mat-khau');
    expect(result.current.user).toEqual(ACCOUNT);
    expect(getToken()).toBe('token-moi');

    act(() => result.current.logout());
    expect(result.current.user).toBeNull();
    expect(getToken()).toBeNull();

    const payload = { email: 'moi@example.com', password: 'x', fullName: 'Mới' };
    await act(() => result.current.register(payload));
    expect(authApi.register).toHaveBeenCalledWith(payload);
    expect(getToken()).toBe('token-dang-ky');
  });

  it('API trả 401 thì đăng xuất', async () => {
    saveToken('token-cu', 3600);
    const { result } = renderAuth();
    await waitFor(() => expect(result.current.user).toEqual(ACCOUNT));
    act(() => window.dispatchEvent(new Event(UNAUTHORIZED_EVENT)));
    expect(result.current.user).toBeNull();
  });
});
