package com.manhdn.fashionshop.dto;

import java.time.LocalDateTime;

/**
 * Một sản phẩm trong danh sách yêu thích.
 *
 * @param product thẻ sản phẩm (giá, còn hàng, điểm đánh giá theo dữ liệu hiện tại)
 * @param addedAt lúc thêm vào danh sách yêu thích
 */
public record WishlistItemResponse(ProductCardResponse product, LocalDateTime addedAt) {}
