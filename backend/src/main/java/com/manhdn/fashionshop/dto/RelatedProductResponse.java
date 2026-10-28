package com.manhdn.fashionshop.dto;

/**
 * Một sản phẩm gợi ý (liên quan US-12, trang chủ US-13, khảo sát US-23) kèm lý do ngắn và nguồn.
 *
 * @param product thẻ sản phẩm
 * @param reason lý do gợi ý, vd "Cùng danh mục với sản phẩm bạn đang xem"
 * @param source nguồn của sản phẩm này trong danh sách, xem {@link RecommendationSource}; frontend
 *     gửi lại khi ghi lượt hiện, lượt bấm để tính tỷ lệ bấm theo nguồn
 */
public record RelatedProductResponse(ProductCardResponse product, String reason, String source) {}
