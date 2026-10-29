import { AxiosError } from 'axios';
import apiClient from '../src/services/apiClient';

/**
 * Thay phần gửi request của apiClient (adapter) bằng hàm giả, nên interceptor vẫn chạy thật.
 * respond(config) trả về { status, data }; status >= 400 thành lỗi giống axios. Trả về danh sách
 * request đã gửi và hàm khôi phục.
 */
export function mockApi(respond = () => ({ status: 200, data: {} })) {
  const original = apiClient.defaults.adapter;
  const requests = [];
  apiClient.defaults.adapter = async (config) => {
    requests.push(config);
    const { status = 200, data = {} } = (await respond(config)) ?? {};
    const response = { status, statusText: String(status), data, headers: {}, config };
    if (status >= 400) {
      throw new AxiosError(`HTTP ${status}`, 'ERR_BAD_RESPONSE', config, null, response);
    }
    return response;
  };
  return {
    requests,
    restore: () => {
      apiClient.defaults.adapter = original;
    },
  };
}
