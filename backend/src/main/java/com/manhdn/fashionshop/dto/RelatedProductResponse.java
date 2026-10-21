package com.manhdn.fashionshop.dto;

/**
 * Một sản phẩm trong mục "Gợi ý sản phẩm liên quan" (US-12) kèm lý do ngắn.
 *
 * @param product thẻ sản phẩm
 * @param reason lý do gợi ý, vd "Cùng danh mục với sản phẩm bạn đang xem"
 */
public record RelatedProductResponse(ProductCardResponse product, String reason) {}
