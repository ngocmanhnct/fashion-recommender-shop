package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.ProductImage;
import java.util.List;

/**
 * Chi tiết sản phẩm cho trang người dùng (US-03): ảnh, giá, mô tả, các phân loại và tồn kho từng
 * biến thể để hiện phân loại còn hàng và trạng thái hết hàng.
 *
 * @param id mã sản phẩm
 * @param name tên
 * @param description mô tả, có thể null
 * @param variationName1 tên tầng phân loại 1, null nếu sản phẩm không có phân loại
 * @param variationName2 tên tầng phân loại 2, null nếu sản phẩm có ít hơn 2 tầng
 * @param category danh mục
 * @param brand thương hiệu, có thể null
 * @param imageUrls ảnh theo thứ tự hiển thị
 * @param variants các biến thể kèm giá và tồn kho
 * @param minPrice giá thấp nhất trong các biến thể, VNĐ
 * @param maxPrice giá cao nhất trong các biến thể, VNĐ
 * @param inStock còn ít nhất 1 biến thể có hàng không
 * @param soldCount số đã bán trong các đơn chưa bị hủy
 * @param ratingAverage điểm đánh giá trung bình làm tròn 1 chữ số thập phân, null nếu chưa có
 * @param reviewCount số đánh giá
 */
public record ProductDetailResponse(
    Long id,
    String name,
    String description,
    String variationName1,
    String variationName2,
    CategoryResponse category,
    BrandResponse brand,
    List<String> imageUrls,
    List<VariantResponse> variants,
    Long minPrice,
    Long maxPrice,
    boolean inStock,
    long soldCount,
    Double ratingAverage,
    long reviewCount) {

  /** Chuyển entity (đã nạp từ CSDL, để có số đã bán và điểm đánh giá) sang DTO. */
  public static ProductDetailResponse from(Product p) {
    PriceStock priceStock = PriceStock.of(p.getVariants());
    return new ProductDetailResponse(
        p.getId(),
        p.getName(),
        p.getDescription(),
        p.getVariationName1(),
        p.getVariationName2(),
        CategoryResponse.from(p.getCategory()),
        BrandResponse.from(p.getBrand()),
        p.getImages().stream().map(ProductImage::getImageUrl).toList(),
        p.getVariants().stream().map(VariantResponse::from).toList(),
        priceStock.minPrice(),
        priceStock.maxPrice(),
        priceStock.inStock(),
        p.getSoldCount(),
        p.getRatingAverage(),
        p.getReviewCount());
  }
}
