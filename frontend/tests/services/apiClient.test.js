import { afterEach, describe, expect, it, vi } from 'vitest';
import apiClient, { UNAUTHORIZED_EVENT } from '../../src/services/apiClient';
import { getToken, saveToken } from '../../src/services/tokenStorage';
import { mockApi } from '../apiMock';

describe('apiClient', () => {
  let api;
  afterEach(() => api?.restore());

  it('gắn token cho API nghiệp vụ, không gắn cho đăng nhập và Actuator', async () => {
    api = mockApi();
    saveToken('tok', 60);
    await apiClient.get('/api/cart');
    await apiClient.post('/api/auth/login', {});
    await apiClient.get('/actuator/health');
    const auth = api.requests.map((r) => r.headers.Authorization);
    expect(auth).toEqual(['Bearer tok', undefined, undefined]);
  });

  it('không gắn gì khi chưa đăng nhập', async () => {
    api = mockApi();
    await apiClient.get('/api/cart');
    expect(api.requests[0].headers.Authorization).toBeUndefined();
  });

  it('401 khi có token: xóa token, báo đăng xuất, gửi lại GET 1 lần không kèm token', async () => {
    api = mockApi((config) =>
      config.headers.Authorization ? { status: 401 } : { data: { ok: true } }
    );
    const listener = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, listener);
    saveToken('cũ', 60);
    const res = await apiClient.get('/api/products/1');
    window.removeEventListener(UNAUTHORIZED_EVENT, listener);
    expect(res.data).toEqual({ ok: true });
    expect(api.requests).toHaveLength(2);
    expect(api.requests[1].headers.Authorization).toBeUndefined();
    expect(listener).toHaveBeenCalledOnce();
    expect(getToken()).toBeNull();
  });

  it('không gửi lại POST sau 401', async () => {
    api = mockApi(() => ({ status: 401 }));
    saveToken('cũ', 60);
    await expect(apiClient.post('/api/cart/items', {})).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(api.requests).toHaveLength(1);
  });

  it('401 khi không gửi token thì chỉ trả lỗi về', async () => {
    api = mockApi(() => ({ status: 401 }));
    const listener = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, listener);
    await expect(apiClient.get('/api/cart')).rejects.toBeTruthy();
    window.removeEventListener(UNAUTHORIZED_EVENT, listener);
    expect(listener).not.toHaveBeenCalled();
  });
});
