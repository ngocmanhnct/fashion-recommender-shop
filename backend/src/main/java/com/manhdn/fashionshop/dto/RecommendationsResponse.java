package com.manhdn.fashionshop.dto;

import java.util.List;

/**
 * Khối "Gợi ý cho bạn" ở trang chủ (US-13).
 *
 * @param source nguồn của danh sách: BEHAVIOR (đủ dữ liệu hành vi, BR-19), SURVEY (chưa đủ, theo
 *     khảo sát lần đầu) hoặc POPULAR (chưa có gợi ý riêng, hiện sản phẩm bán chạy thay thế)
 * @param products sản phẩm kèm lý do gợi ý, theo thứ hạng
 * @param recent hàng "Vì bạn vừa xem / vừa thêm vào giỏ" theo hành động gần nhất trong 24 giờ; null
 *     nếu không có
 * @param updatedSecondsAgo danh sách được ml-service tính cách đây bao nhiêu giây (tính trên máy
 *     chủ, không phụ thuộc múi giờ của trình duyệt); null khi POPULAR
 * @param updating true khi tài khoản còn yêu cầu tính lại gợi ý chưa xử lý: danh sách sắp đổi
 * @param abVariant nhóm của khách trong thử nghiệm A/B đang chạy (TREATMENT hoặc CONTROL, US-27);
 *     null khi không tham gia
 */
public record RecommendationsResponse(
    String source,
    List<RelatedProductResponse> products,
    RecentActivityResponse recent,
    Long updatedSecondsAgo,
    boolean updating,
    String abVariant) {}
