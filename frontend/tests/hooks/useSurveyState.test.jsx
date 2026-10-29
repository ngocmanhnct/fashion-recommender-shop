import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/context/authContext';
import { useSurveyState } from '../../src/hooks/useSurveyState';
import * as surveyApi from '../../src/services/surveyApi';
import { readLocalSurvey, writeLocalSurvey } from '../../src/services/surveyStorage';
import { authValue } from '../renderWithContext';

vi.mock('../../src/services/surveyApi');

const ANSWERS = [{ questionCode: 'Q1', optionCodes: ['SELF'] }];

// Mỗi test một mã tài khoản: lần đồng bộ đang chạy được nhớ theo tài khoản ở cấp module.
let nextAccountId = 100;

function renderSurvey(user, loading = false) {
  const auth = authValue({ user, loading });
  return renderHook(() => useSurveyState(), {
    wrapper: ({ children }) => <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>,
  });
}

function customer() {
  nextAccountId += 1;
  return { id: nextAccountId, role: 'CUSTOMER' };
}

describe('useSurveyState', () => {
  beforeEach(() => {
    vi.mocked(surveyApi.getMySurvey).mockResolvedValue({ status: 'NONE', answers: [] });
  });

  it('đang kiểm tra đăng nhập thì đang tải', () => {
    const { result } = renderSurvey(null, true);
    expect(result.current).toMatchObject({ status: null, loading: true });
  });

  it('khách chưa đăng nhập đọc khảo sát từ trình duyệt, bỏ qua thì ghi lại', async () => {
    const { result } = renderSurvey(null);
    expect(result.current).toMatchObject({ status: 'NONE', answers: [], loading: false });

    await act(() => result.current.skip());
    expect(result.current.status).toBe('SKIPPED');
    expect(readLocalSurvey()).toEqual({ status: 'SKIPPED', answers: [] });
    expect(surveyApi.skipMySurvey).not.toHaveBeenCalled();
  });

  it('quản trị viên không làm khảo sát', () => {
    const { result } = renderSurvey({ id: 1, role: 'ADMIN' });
    expect(result.current).toMatchObject({ status: null, loading: false });
    expect(surveyApi.getMySurvey).not.toHaveBeenCalled();
  });

  it('khách hàng đọc khảo sát của tài khoản', async () => {
    vi.mocked(surveyApi.getMySurvey).mockResolvedValue({ status: 'COMPLETED', answers: ANSWERS });
    const { result } = renderSurvey(customer());
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current).toMatchObject({ status: 'COMPLETED', answers: ANSWERS });
  });

  it('gửi kết quả làm lúc chưa đăng nhập lên tài khoản rồi xóa khỏi trình duyệt', async () => {
    writeLocalSurvey('COMPLETED', ANSWERS);
    vi.mocked(surveyApi.saveMySurvey).mockResolvedValue({ status: 'COMPLETED', answers: ANSWERS });
    const { result } = renderSurvey(customer());
    await waitFor(() => expect(result.current.status).toBe('COMPLETED'));
    expect(surveyApi.saveMySurvey).toHaveBeenCalledWith(ANSWERS);
    expect(readLocalSurvey()).toBeNull();
  });

  it('ghi nhận bỏ qua lúc chưa đăng nhập cho tài khoản', async () => {
    writeLocalSurvey('SKIPPED');
    vi.mocked(surveyApi.skipMySurvey).mockResolvedValue({ status: 'SKIPPED', answers: [] });
    const { result } = renderSurvey(customer());
    await waitFor(() => expect(result.current.status).toBe('SKIPPED'));
    expect(surveyApi.skipMySurvey).toHaveBeenCalledTimes(1);
  });

  it('không gửi lên khi tài khoản đã làm hoặc khách vừa tìm quà, chỉ xóa trình duyệt', async () => {
    writeLocalSurvey('GIFT', ANSWERS);
    const { result } = renderSurvey(customer());
    await waitFor(() => expect(result.current.status).toBe('NONE'));
    expect(surveyApi.saveMySurvey).not.toHaveBeenCalled();
    expect(readLocalSurvey()).toBeNull();
  });

  it('câu trả lời cũ bị từ chối thì bỏ và mời làm lại', async () => {
    writeLocalSurvey('COMPLETED', ANSWERS);
    vi.mocked(surveyApi.saveMySurvey).mockRejectedValue(new Error('cây câu hỏi đã đổi'));
    const { result } = renderSurvey(customer());
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.status).toBe('NONE');
    expect(readLocalSurvey()).toBeNull();
  });

  it('tải lỗi thì coi như đã làm, không mời làm khảo sát', async () => {
    vi.mocked(surveyApi.getMySurvey).mockRejectedValue(new Error('mất mạng'));
    const { result } = renderSurvey(customer());
    await waitFor(() => expect(result.current.status).toBe('SKIPPED'));
  });

  it('khách hàng bấm bỏ qua thì gọi API', async () => {
    vi.mocked(surveyApi.skipMySurvey).mockResolvedValue({ status: 'SKIPPED', answers: [] });
    const { result } = renderSurvey(customer());
    await waitFor(() => expect(result.current.status).toBe('NONE'));
    await act(() => result.current.skip());
    expect(result.current.status).toBe('SKIPPED');
  });

  it('2 nơi cùng tải chỉ gửi 1 request', async () => {
    const user = customer();
    const first = renderSurvey(user);
    const second = renderSurvey(user);
    await waitFor(() => expect(first.result.current.loading).toBe(false));
    await waitFor(() => expect(second.result.current.loading).toBe(false));
    expect(surveyApi.getMySurvey).toHaveBeenCalledTimes(1);
  });
});
