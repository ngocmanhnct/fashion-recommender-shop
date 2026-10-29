import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useApiData } from '../../src/hooks/useApiData';

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe('useApiData', () => {
  it('không tải khi requestKey là null', () => {
    const load = vi.fn();
    const { result } = renderHook(() => useApiData(null, load));
    expect(load).not.toHaveBeenCalled();
    expect(result.current).toMatchObject({ loading: false, data: null, error: null });
  });

  it('đang tải rồi trả dữ liệu của requestKey', async () => {
    const load = vi.fn((key) => Promise.resolve(`dữ liệu ${key}`));
    const { result } = renderHook(() => useApiData('a', load));
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toBe('dữ liệu a');
    expect(load).toHaveBeenCalledWith('a');
  });

  it('trả lỗi khi tải hỏng và reload tải lại cùng requestKey', async () => {
    const load = vi.fn().mockRejectedValueOnce(new Error('hỏng')).mockResolvedValueOnce('ổn');
    const { result } = renderHook(() => useApiData('a', load));
    await waitFor(() => expect(result.current.error?.message).toBe('hỏng'));
    expect(result.current.data).toBeNull();

    act(() => result.current.reload());
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.data).toBe('ổn'));
    expect(result.current.error).toBeNull();
    expect(load).toHaveBeenCalledTimes(2);
  });

  it('giữ lastData khi đổi requestKey và bỏ qua phản hồi muộn của request cũ', async () => {
    const first = deferred();
    const second = deferred();
    const load = vi.fn((key) => (key === 'a' ? first.promise : second.promise));
    const { result, rerender } = renderHook(({ key }) => useApiData(key, load), {
      initialProps: { key: 'a' },
    });
    rerender({ key: 'b' });
    await act(async () => second.resolve('kết quả b'));
    expect(result.current.data).toBe('kết quả b');

    await act(async () => first.resolve('kết quả a cũ'));
    expect(result.current.data).toBe('kết quả b');

    const third = deferred();
    load.mockImplementation(() => third.promise);
    rerender({ key: 'c' });
    expect(result.current.loading).toBe(true);
    expect(result.current.data).toBeNull();
    expect(result.current.lastData).toBe('kết quả b');
  });
});
