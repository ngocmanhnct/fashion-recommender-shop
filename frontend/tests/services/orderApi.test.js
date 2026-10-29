import { afterEach, describe, expect, it, vi } from 'vitest';
import { newIdempotencyKey } from '../../src/services/orderApi';
import { goToVnPay } from '../../src/services/vnpayRedirect';
import { mockApi } from '../apiMock';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('newIdempotencyKey', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('dùng crypto.randomUUID khi có', () => {
    expect(newIdempotencyKey()).toMatch(UUID_V4);
  });

  it('trang http không có randomUUID vẫn tạo được UUID v4', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: (bytes) => bytes.fill(0xff),
    });
    const key = newIdempotencyKey();
    expect(key).toMatch(UUID_V4);
    expect(key).toBe('ffffffff-ffff-4fff-bfff-ffffffffffff');
  });
});

describe('goToVnPay', () => {
  let api;
  afterEach(() => api.restore());

  it('chuyển sang trang VNPay khi lấy được đường dẫn', async () => {
    api = mockApi(() => ({ data: { paymentUrl: 'https://sandbox.vnpayment.vn/pay' } }));
    const assign = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({ assign });
    const navigate = vi.fn();
    await goToVnPay(12, navigate);
    expect(assign).toHaveBeenCalledWith('https://sandbox.vnpayment.vn/pay');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('lỗi thì về trang chi tiết đơn kèm thông báo', async () => {
    api = mockApi(() => ({ status: 409, data: { detail: 'Đơn đã hết hạn thanh toán' } }));
    const navigate = vi.fn();
    await goToVnPay(12, navigate);
    expect(navigate).toHaveBeenCalledWith('/orders/12', {
      replace: true,
      state: { paymentError: 'Đơn đã hết hạn thanh toán' },
    });
  });
});
