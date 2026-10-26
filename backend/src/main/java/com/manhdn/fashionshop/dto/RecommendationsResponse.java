package com.manhdn.fashionshop.dto;

import java.util.List;

/**
 * Khối "Gợi ý cho bạn" ở trang chủ (US-13).
 *
 * @param source nguồn của danh sách: BEHAVIOR (đủ dữ liệu hành vi, BR-19), SURVEY (chưa đủ, theo
 *     khảo sát lần đầu) hoặc POPULAR (chưa có gợi ý riêng, hiện sản phẩm bán chạy thay thế)
 * @param products sản phẩm kèm lý do gợi ý, theo thứ hạng
 */
public record RecommendationsResponse(String source, List<RelatedProductResponse> products) {}
