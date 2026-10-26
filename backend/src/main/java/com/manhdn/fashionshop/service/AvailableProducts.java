package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.ProductCardResponse;
import com.manhdn.fashionshop.repository.ProductRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Tra thẻ sản phẩm theo mã cho các khối gợi ý, chỉ giữ sản phẩm đang bán và còn hàng. */
final class AvailableProducts {

  private AvailableProducts() {}

  /**
   * Thẻ của các sản phẩm đang bán, còn hàng trong danh sách mã (gợi ý của ml-service có thể cũ hơn
   * dữ liệu hiện tại vài giây).
   *
   * @param productRepository nơi đọc sản phẩm
   * @param ids các mã sản phẩm
   * @return thẻ sản phẩm theo mã; mã không còn bán hoặc hết hàng thì không có trong kết quả
   */
  static Map<Long, ProductCardResponse> byId(
      ProductRepository productRepository, Collection<Long> ids) {
    Map<Long, ProductCardResponse> cards = new HashMap<>();
    if (ids.isEmpty()) {
      return cards;
    }
    productRepository.findAllById(ids).stream()
        .filter(p -> !p.isDeleted())
        .map(ProductCardResponse::from)
        .filter(ProductCardResponse::inStock)
        .forEach(card -> cards.put(card.id(), card));
    return cards;
  }
}
