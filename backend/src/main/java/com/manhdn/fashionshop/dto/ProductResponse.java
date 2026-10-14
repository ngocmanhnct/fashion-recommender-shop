package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.ProductImage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chi tiết sản phẩm trả về cho trang quản trị.
 *
 * @param id mã sản phẩm
 * @param name tên
 * @param description mô tả
 * @param category danh mục
 * @param brand thương hiệu, có thể null
 * @param imageUrls ảnh theo thứ tự hiển thị
 * @param variants các biến thể
 * @param deletedAt thời điểm xóa mềm, null nếu đang bán
 * @param createdAt thời điểm tạo
 * @param updatedAt thời điểm sửa gần nhất
 */
public record ProductResponse(
    Long id,
    String name,
    String description,
    CategoryResponse category,
    BrandResponse brand,
    List<String> imageUrls,
    List<VariantResponse> variants,
    LocalDateTime deletedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  /** Chuyển entity sang DTO. */
  public static ProductResponse from(Product p) {
    return new ProductResponse(
        p.getId(),
        p.getName(),
        p.getDescription(),
        CategoryResponse.from(p.getCategory()),
        BrandResponse.from(p.getBrand()),
        p.getImages().stream().map(ProductImage::getImageUrl).toList(),
        p.getVariants().stream().map(VariantResponse::from).toList(),
        p.getDeletedAt(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
