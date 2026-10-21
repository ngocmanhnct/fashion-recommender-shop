import { useCallback, useEffect, useEffectEvent, useState } from 'react';

/**
 * Tải dữ liệu từ API theo requestKey (vd chuỗi truy vấn, mã sản phẩm) và trả về
 * { data, error, loading, lastData, reload }.
 *
 * - load(requestKey) trả về Promise; requestKey = null thì không tải.
 * - Chỉ tải lại khi requestKey đổi, nên requestKey phải mang đủ thông tin của request.
 * - Kết quả lưu kèm requestKey: loading = kết quả đang có không phải của requestKey hiện tại.
 *   Không gọi setState ngay đầu effect (luật react-hooks/set-state-in-effect), và phản hồi về
 *   muộn của request cũ bị bỏ qua (cờ ignore), nên không bị kết quả cũ đè kết quả mới.
 * - data, error chỉ là của requestKey hiện tại; lastData là dữ liệu của lần tải xong gần nhất
 *   (có thể của requestKey trước), dùng để giữ danh sách cũ trên màn hình trong lúc tải.
 * - reload() tải lại cùng requestKey, dùng cho nút "Thử lại" sau khi tải lỗi.
 */
export function useApiData(requestKey, load) {
  const [state, setState] = useState({ key: null, attempt: 0, data: null, error: null });
  // Tăng lên mỗi lần bấm "Thử lại": cùng requestKey nhưng là một lần tải mới.
  const [attempt, setAttempt] = useState(0);
  const runLoad = useEffectEvent(load);

  useEffect(() => {
    if (requestKey === null) return undefined;
    let ignore = false;
    runLoad(requestKey).then(
      (data) => {
        if (!ignore) setState({ key: requestKey, attempt, data, error: null });
      },
      (error) => {
        if (!ignore) setState({ key: requestKey, attempt, data: null, error });
      }
    );
    return () => {
      ignore = true;
    };
  }, [requestKey, attempt]);

  const reload = useCallback(() => setAttempt((n) => n + 1), []);
  const loading = requestKey !== null && (state.key !== requestKey || state.attempt !== attempt);
  return {
    loading,
    data: loading ? null : state.data,
    error: loading ? null : state.error,
    lastData: state.data,
    reload,
  };
}
