import { useCallback, useEffect, useState } from 'react';
import * as surveyApi from '../services/surveyApi';
import { clearLocalSurvey, readLocalSurvey, writeLocalSurvey } from '../services/surveyStorage';
import { useAuth } from './useAuth';

// Lần đồng bộ đang chạy theo tài khoản: trang chủ, trang khảo sát và StrictMode (chạy effect 2
// lần) dùng chung 1 request, nên câu trả lời của trình duyệt không bị gửi 2 lần.
let inflight = null;

/**
 * Khảo sát của tài khoản; trình duyệt còn kết quả làm lúc chưa đăng nhập mà tài khoản chưa có thì
 * gửi lên trước (làm xong thì lưu, bỏ qua thì ghi nhận bỏ qua), rồi xóa khỏi trình duyệt.
 */
function loadCustomerSurvey(accountId) {
  if (inflight?.accountId === accountId) return inflight.promise;
  const promise = surveyApi.getMySurvey().then((mine) => {
    const local = readLocalSurvey();
    if (!local) return mine;
    if (mine.status !== 'NONE' || local.status === 'GIFT') {
      clearLocalSurvey();
      return mine;
    }
    const sync =
      local.status === 'COMPLETED'
        ? surveyApi.saveMySurvey(local.answers)
        : surveyApi.skipMySurvey();
    // Câu trả lời cũ không còn hợp lệ (cây câu hỏi đã đổi) thì bỏ, khách sẽ được mời làm lại.
    return sync.then(
      (saved) => {
        clearLocalSurvey();
        return saved;
      },
      () => {
        clearLocalSurvey();
        return mine;
      }
    );
  });
  inflight = { accountId, promise };
  const clear = () => {
    if (inflight?.promise === promise) inflight = null;
  };
  promise.then(clear, clear);
  return promise;
}

function guestState() {
  const local = readLocalSurvey();
  return local ?? { status: 'NONE', answers: [] };
}

/**
 * Trạng thái khảo sát của người đang xem (US-23): { status, answers, loading, skip }.
 * - Khách chưa đăng nhập: đọc từ localStorage.
 * - Khách hàng: đọc từ backend (đồng bộ kết quả của trình duyệt nếu có).
 * - Quản trị viên: không làm khảo sát, status = null.
 * status: NONE (chưa làm), COMPLETED, SKIPPED, GIFT (chỉ ở trình duyệt).
 */
export function useSurveyState() {
  const { user, loading: authLoading } = useAuth();
  const isCustomer = user?.role === 'CUSTOMER';
  const accountId = isCustomer ? user.id : null;
  const [remote, setRemote] = useState({ accountId: null, data: null });
  // Chỉ để vẽ lại sau khi khách chưa đăng nhập bấm bỏ qua; dữ liệu luôn đọc mới từ localStorage.
  const [, setGuestVersion] = useState(0);

  useEffect(() => {
    if (accountId === null) return undefined;
    let ignore = false;
    loadCustomerSurvey(accountId).then(
      (data) => {
        if (!ignore) setRemote({ accountId, data });
      },
      () => {
        // Lỗi tải thì coi như đã làm: không mời làm khảo sát khi chưa biết chắc.
        if (!ignore) setRemote({ accountId, data: { status: 'SKIPPED', answers: [] } });
      }
    );
    return () => {
      ignore = true;
    };
  }, [accountId]);

  const skip = useCallback(async () => {
    if (accountId === null) {
      writeLocalSurvey('SKIPPED');
      setGuestVersion((n) => n + 1);
      return;
    }
    const data = await surveyApi.skipMySurvey();
    setRemote({ accountId, data });
  }, [accountId]);

  if (authLoading) return { status: null, answers: [], loading: true, skip };
  if (!user) {
    return { ...guestState(), loading: false, skip };
  }
  if (!isCustomer) return { status: null, answers: [], loading: false, skip };
  if (remote.accountId !== accountId) return { status: null, answers: [], loading: true, skip };
  return { status: remote.data.status, answers: remote.data.answers ?? [], loading: false, skip };
}
