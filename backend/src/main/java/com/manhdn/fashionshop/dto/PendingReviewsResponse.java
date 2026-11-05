package com.manhdn.fashionshop.dto;

import java.util.List;

/**
 * Các sản phẩm chờ đánh giá của khách hàng.
 *
 * @param total tổng số sản phẩm chờ đánh giá
 * @param items tối đa {@code ReviewService.MAX_PENDING} sản phẩm, đơn mới nhất trước
 */
public record PendingReviewsResponse(long total, List<PendingReviewResponse> items) {}
