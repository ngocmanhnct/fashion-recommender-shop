package com.manhdn.fashionshop.dto;

import java.util.List;

/**
 * Hàng "Vì bạn vừa xem / vừa thêm vào giỏ" ở trang chủ: sản phẩm khách vừa tương tác và các sản
 * phẩm liên quan, tính ngay lúc mở trang nên đổi theo từng hành động.
 *
 * @param product sản phẩm khách vừa xem hoặc vừa thêm vào giỏ
 * @param kind VIEW (vừa xem) hoặc CART (vừa thêm vào giỏ)
 * @param products sản phẩm liên quan kèm lý do và nguồn, như khối sản phẩm liên quan (US-12)
 */
public record RecentActivityResponse(
    ProductCardResponse product, String kind, List<RelatedProductResponse> products) {}
