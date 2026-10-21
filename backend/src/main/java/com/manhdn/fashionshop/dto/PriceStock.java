package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.ProductVariant;
import java.util.List;
import java.util.LongSummaryStatistics;

/**
 * Khoảng giá và trạng thái còn hàng tính từ các biến thể đã nạp của một sản phẩm.
 *
 * @param minPrice giá thấp nhất, null nếu không có biến thể
 * @param maxPrice giá cao nhất, null nếu không có biến thể
 * @param inStock có biến thể nào tồn kho lớn hơn 0 không
 */
record PriceStock(Long minPrice, Long maxPrice, boolean inStock) {

  /** Tính từ danh sách biến thể. */
  static PriceStock of(List<ProductVariant> variants) {
    LongSummaryStatistics prices =
        variants.stream().mapToLong(ProductVariant::getPrice).summaryStatistics();
    boolean inStock = variants.stream().anyMatch(v -> v.getStock() > 0);
    return prices.getCount() == 0
        ? new PriceStock(null, null, inStock)
        : new PriceStock(prices.getMin(), prices.getMax(), inStock);
  }
}
