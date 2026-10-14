package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Brand;
import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.ProductVariant;
import java.util.LongSummaryStatistics;

/**
 * Một dòng trong danh sách sản phẩm của trang quản trị.
 *
 * @param id mã sản phẩm
 * @param name tên
 * @param categoryName tên danh mục
 * @param brandName tên thương hiệu, có thể null
 * @param thumbnailUrl ảnh đầu tiên, có thể null
 * @param minPrice giá thấp nhất trong các biến thể
 * @param maxPrice giá cao nhất trong các biến thể
 * @param totalStock tổng tồn kho các biến thể
 * @param variantCount số biến thể
 * @param deleted đã xóa mềm hay chưa
 */
public record ProductSummaryResponse(
    Long id,
    String name,
    String categoryName,
    String brandName,
    String thumbnailUrl,
    Long minPrice,
    Long maxPrice,
    long totalStock,
    int variantCount,
    boolean deleted) {

  /** Chuyển entity sang DTO. */
  public static ProductSummaryResponse from(Product p) {
    LongSummaryStatistics prices =
        p.getVariants().stream().mapToLong(ProductVariant::getPrice).summaryStatistics();
    long totalStock = p.getVariants().stream().mapToLong(ProductVariant::getStock).sum();
    Brand brand = p.getBrand();
    return new ProductSummaryResponse(
        p.getId(),
        p.getName(),
        p.getCategory().getName(),
        brand == null ? null : brand.getName(),
        p.getImages().isEmpty() ? null : p.getImages().get(0).getImageUrl(),
        prices.getCount() == 0 ? null : prices.getMin(),
        prices.getCount() == 0 ? null : prices.getMax(),
        totalStock,
        p.getVariants().size(),
        p.isDeleted());
  }
}
