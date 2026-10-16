package com.manhdn.fashionshop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Danh mục sản phẩm mẫu đọc từ file JSON (mặc định {@code resources/seed/tiki-catalog.json}, dựng
 * từ bộ dữ liệu Tiki bằng {@code ml-service/scripts/build_tiki_catalog.py}).
 *
 * @param source nguồn dữ liệu, chỉ để truy vết
 * @param products các sản phẩm, nạp theo đúng thứ tự trong file
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogSeed(String source, List<SeedProduct> products) {

  /**
   * Một sản phẩm trong file mẫu. Danh mục và thương hiệu ghi bằng tên; các trường khác của file (mã
   * sản phẩm, số lượng đã bán, điểm đánh giá trên Tiki) chỉ để truy vết và đánh giá, không nạp vào
   * CSDL.
   *
   * @param category tên danh mục, bắt buộc
   * @param brand tên thương hiệu, null = không có thương hiệu
   * @param name tên sản phẩm
   * @param description mô tả, tùy chọn
   * @param variationName1 tên tầng phân loại 1, null = không có phân loại
   * @param variationName2 tên tầng phân loại 2, null = chỉ có 1 tầng
   * @param variants các biến thể
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SeedProduct(
      String category,
      String brand,
      String name,
      String description,
      String variationName1,
      String variationName2,
      List<VariantRequest> variants) {}
}
