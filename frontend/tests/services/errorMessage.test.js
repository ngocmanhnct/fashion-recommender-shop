import { describe, expect, it } from 'vitest';
import { getErrorMessage, getFieldErrors } from '../../src/services/errorMessage';

describe('errorMessage', () => {
  it('báo không kết nối được khi không có phản hồi', () => {
    expect(getErrorMessage(new Error('Network Error'))).toMatch('Không kết nối được');
  });

  it('dùng detail của ProblemDetail, thiếu thì dùng thông báo dự phòng', () => {
    expect(getErrorMessage({ response: { data: { detail: 'Hết hàng' } } })).toBe('Hết hàng');
    expect(getErrorMessage({ response: { data: {} } }, 'Lỗi riêng')).toBe('Lỗi riêng');
    expect(getErrorMessage({ response: {} })).toBe('Có lỗi xảy ra, vui lòng thử lại');
  });

  it('đọc lỗi từng trường, không có thì trả về object rỗng', () => {
    const error = { response: { data: { errors: { email: 'Email không hợp lệ' } } } };
    expect(getFieldErrors(error)).toEqual({ email: 'Email không hợp lệ' });
    expect(getFieldErrors(new Error('x'))).toEqual({});
  });
});
