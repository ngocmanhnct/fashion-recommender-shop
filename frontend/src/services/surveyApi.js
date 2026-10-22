import apiClient from './apiClient';

// API khảo sát lần đầu (US-23, BR-37, BR-38). Câu trả lời là danh sách theo đúng thứ tự đi trên
// cây câu hỏi: [{ questionCode, optionCodes }] hoặc { questionCode, brandId } với câu chọn thương
// hiệu (brandId null = không có thương hiệu ưa thích). 3 API đầu công khai, /me chỉ cho khách hàng.
const BASE = '/api/survey';

/** Cây câu hỏi: { startQuestionCode, questions }. */
export function getSurvey() {
  return apiClient.get(BASE).then((res) => res.data);
}

/** Thương hiệu để chọn ở câu chọn thương hiệu, theo các câu đã trả lời trước đó. */
export function getSurveyBrands(answers) {
  return apiClient.post(`${BASE}/brands`, { answers }).then((res) => res.data);
}

/** Hồ sơ mua sắm và sản phẩm gợi ý theo câu trả lời, không lưu gì: { profile, products }. */
export function evaluateSurvey(answers) {
  return apiClient.post(`${BASE}/evaluate`, { answers }).then((res) => res.data);
}

/** Khảo sát của tài khoản: { status: NONE | COMPLETED | SKIPPED, answers, profile }. */
export function getMySurvey() {
  return apiClient.get(`${BASE}/me`).then((res) => res.data);
}

/** Lưu câu trả lời vào sở thích của tài khoản (làm lại thì thay toàn bộ). */
export function saveMySurvey(answers) {
  return apiClient.put(`${BASE}/me`, { answers }).then((res) => res.data);
}

/** Ghi nhận khách bỏ qua khảo sát; đã làm xong thì giữ nguyên kết quả cũ. */
export function skipMySurvey() {
  return apiClient.post(`${BASE}/me/skip`).then((res) => res.data);
}
