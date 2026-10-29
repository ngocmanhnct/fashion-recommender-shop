import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearToken, getToken, saveToken } from '../../src/services/tokenStorage';

describe('tokenStorage', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('trả về token còn hạn', () => {
    saveToken('abc', 60);
    expect(getToken()).toBe('abc');
  });

  it('bỏ token đã hết hạn', () => {
    vi.useFakeTimers();
    saveToken('abc', 60);
    vi.advanceTimersByTime(60_000);
    expect(getToken()).toBeNull();
    expect(localStorage.getItem('fashionshop.token')).toBeNull();
  });

  it('bỏ token không có thời điểm hết hạn', () => {
    localStorage.setItem('fashionshop.token', 'abc');
    expect(getToken()).toBeNull();
  });

  it('clearToken xóa cả token và hạn', () => {
    saveToken('abc', 60);
    clearToken();
    expect(localStorage.length).toBe(0);
  });
});
