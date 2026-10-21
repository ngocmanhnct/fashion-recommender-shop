package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Brand;
import com.manhdn.fashionshop.entity.Product;

/**
 * Một thẻ sản phẩm trong danh sách, kết quả tìm kiếm và các khối gợi ý của trang người dùng.
 *
 * @param id mã sản phẩm
 * @param name tên
 * @param categoryName tên danh mục
 * @param brandName tên thương hiệu, có thể null
 * @param thumbnailUrl ảnh đầu tiên, có thể null
 * @param minPrice giá thấp nhất trong các biến thể, VNĐ
 * @param maxPrice giá cao nhất trong các biến thể, VNĐ
 * @param inStock còn ít nhất 1 biến thể có hàng không
 * @param soldCount số đã bán trong các đơn chưa bị hủy
 * @param ratingAverage điểm đánh giá trung bình làm tròn 1 chữ số thập phân, null nếu chưa có
 * @param reviewCount số đánh giá
 */
public record ProductCardResponse(
    Long id,
    String name,
    String categoryName,
    String brandName,
    String thumbnailUrl,
    Long minPrice,
    Long maxPrice,
    boolean inStock,
    long soldCount,
    Double ratingAverage,
    long reviewCount) {

  /** Chuyển entity (đã nạp từ CSDL, để có số đã bán và điểm đánh giá) sang DTO. */
  public static ProductCardResponse from(Product p) {
    PriceStock priceStock = PriceStock.of(p.getVariants());
    Brand brand = p.getBrand();
    return new ProductCardResponse(
        p.getId(),
        p.getName(),
        p.getCategory().getName(),
        brand == null ? null : brand.getName(),
        p.getImages().isEmpty() ? null : p.getImages().get(0).getImageUrl(),
        priceStock.minPrice(),
        priceStock.maxPrice(),
        priceStock.inStock(),
        p.getSoldCount(),
        p.getRatingAverage(),
        p.getReviewCount());
  }
}
