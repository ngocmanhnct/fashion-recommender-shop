package com.manhdn.fashionshop.dto;

import java.util.List;

/**
 * Phần đánh giá của trang sản phẩm.
 *
 * @param ratingAverage điểm trung bình làm tròn 1 chữ số thập phân, null nếu chưa có đánh giá
 * @param reviewCount tổng số đánh giá
 * @param starCounts số đánh giá theo mức sao, phần tử 0 là 1 sao, phần tử 4 là 5 sao
 * @param reviews một trang đánh giá, sửa gần nhất trước
 */
public record ProductReviewsResponse(
    Double ratingAverage,
    long reviewCount,
    List<Long> starCounts,
    PageResponse<ReviewResponse> reviews) {}
