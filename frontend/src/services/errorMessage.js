// Backend trả lỗi theo chuẩn ProblemDetail (RFC 9457): { status, title, detail, errors? }.
// "errors" chỉ có ở lỗi validate (400): tên trường → thông báo.

export function getErrorMessage(error, fallback = 'Có lỗi xảy ra, vui lòng thử lại') {
  if (!error.response) {
    return 'Không kết nối được tới máy chủ. Kiểm tra backend đã chạy chưa.';
  }
  return error.response.data?.detail || fallback;
}

export function getFieldErrors(error) {
  return error.response?.data?.errors || {};
}
