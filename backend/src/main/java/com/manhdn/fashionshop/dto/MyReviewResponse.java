package com.manhdn.fashionshop.dto;

/**
 * Trạng thái đánh giá của khách hàng đang đăng nhập cho một sản phẩm.
 *
 * @param canReview đã có đơn Đã giao chứa sản phẩm này chưa (BR-17)
 * @param review đánh giá đã gửi, null nếu chưa đánh giá
 */
public record MyReviewResponse(boolean canReview, ReviewResponse review) {}
