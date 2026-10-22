// Khảo sát của khách chưa đăng nhập lưu trong localStorage (US-23): { status, answers }.
// - COMPLETED: đã làm xong cho bản thân, answers dùng để gợi ý ở trang chủ và được lưu vào tài
//   khoản ngay sau khi đăng nhập.
// - SKIPPED: đã bỏ qua, không hỏi lại; đăng nhập thì ghi nhận bỏ qua cho tài khoản.
// - GIFT: vừa tìm quà tặng, chỉ để ẩn lời mời làm khảo sát, không gửi lên tài khoản.
// Dữ liệu đọc ra được kiểm tra lại vì có thể bị sửa tay (backend vẫn kiểm tra lần nữa).

const STORAGE_KEY = 'fashionshop.survey';
const STATUSES = new Set(['COMPLETED', 'SKIPPED', 'GIFT']);

function isValidAnswer(answer) {
  return (
    answer !== null &&
    typeof answer === 'object' &&
    typeof answer.questionCode === 'string' &&
    (answer.optionCodes === undefined || Array.isArray(answer.optionCodes))
  );
}

/** Trạng thái đang lưu, hoặc null nếu chưa có hay sai định dạng. */
export function readLocalSurvey() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    if (!parsed || !STATUSES.has(parsed.status)) return null;
    const answers = Array.isArray(parsed.answers) ? parsed.answers.filter(isValidAnswer) : [];
    if (parsed.status === 'COMPLETED' && answers.length === 0) return null;
    return { status: parsed.status, answers };
  } catch {
    return null;
  }
}

export function writeLocalSurvey(status, answers = []) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ status, answers }));
  } catch {
    /* localStorage bị chặn: lần sau khách sẽ được mời làm khảo sát lại. */
  }
}

export function clearLocalSurvey() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* Không xóa được thì thôi, lần đồng bộ sau backend vẫn giữ kết quả đã lưu. */
  }
}
