package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Review;
import java.time.LocalDateTime;

/**
 * Một đánh giá hiển thị ở trang sản phẩm.
 *
 * @param id mã đánh giá
 * @param reviewerName tên gọi của người đánh giá (chữ cuối của họ tên), không lộ họ tên đầy đủ
 * @param rating số sao từ 1 đến 5
 * @param comment nhận xét, có thể null
 * @param variantLabel phân loại đã mua (vd "Đen – 42"), có thể null
 * @param createdAt lúc đánh giá lần đầu
 * @param updatedAt lúc sửa gần nhất
 */
public record ReviewResponse(
    Long id,
    String reviewerName,
    int rating,
    String comment,
    String variantLabel,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  /** Tên hiện khi họ tên trống. */
  static final String ANONYMOUS = "Khách hàng";

  /** Chuyển entity (đã nạp tài khoản và dòng đơn) sang DTO. */
  public static ReviewResponse from(Review review) {
    return new ReviewResponse(
        review.getId(),
        displayName(review.getAccount().getFullName()),
        review.getRating(),
        review.getComment(),
        review.getOrderItem().getVariantLabelSnapshot(),
        review.getCreatedAt(),
        review.getUpdatedAt());
  }

  /** Chữ cuối của họ tên (tên gọi trong tiếng Việt), vd "Đoàn Ngọc Mạnh" thành "Mạnh". */
  static String displayName(String fullName) {
    if (fullName == null || fullName.isBlank()) {
      return ANONYMOUS;
    }
    String[] words = fullName.trim().split("\\s+");
    return words[words.length - 1];
  }
}
