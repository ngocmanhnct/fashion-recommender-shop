// Nhãn hiển thị của trang "Hiệu quả gợi ý" (US-27): vị trí, nguồn gợi ý và kết luận thử nghiệm A/B.

const P_VALUE = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 4 });

export const PLACEMENT_LABELS = {
  HOME: 'Trang chủ: Gợi ý cho bạn',
  HOME_RECENT: 'Trang chủ: Vì bạn vừa xem',
  PRODUCT_DETAIL: 'Chi tiết sản phẩm: sản phẩm liên quan',
  SURVEY_RESULT: 'Kết quả khảo sát',
  CART: 'Giỏ hàng: thường được mua cùng',
};

export const SOURCE_LABELS = {
  BEHAVIOR: 'Học máy, theo hành vi',
  SURVEY: 'Học máy, theo khảo sát',
  RELATED: 'Học máy, sản phẩm tương tự',
  RULE: 'Theo quy tắc (dự phòng)',
  POPULAR: 'Bán chạy',
};

/** Câu kết luận của một phép so sánh (BR-46). */
export function describeVerdict(comparison, minUsers) {
  const p = comparison.pValue == null ? null : P_VALUE.format(comparison.pValue);
  switch (comparison.verdict) {
    case 'TREATMENT_BETTER':
      return `Nhóm gợi ý cao hơn rõ rệt (p = ${p} < 0,05).`;
    case 'CONTROL_BETTER':
      return `Nhóm bán chạy cao hơn rõ rệt (p = ${p} < 0,05).`;
    case 'NO_DIFFERENCE':
      return `Chưa thấy khác biệt có ý nghĩa (p = ${p} ≥ 0,05).`;
    default:
      return `Chưa đủ khách để kết luận: mỗi nhóm cần ít nhất ${minUsers} khách.`;
  }
}
