package com.manhdn.fashionshop.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Khóa so sánh giá trị phân loại phải coi là trùng đúng những cặp mà collation utf8mb4_0900_ai_ci
 * của MySQL coi là trùng (đã thử trên MySQL 8.4), nếu không service sẽ cho qua rồi chỉ mục
 * uk_product_variant_options mới chặn.
 */
class ProductVariantTest {

  @Test
  void optionKeyIgnoresCaseAndVietnameseMarksLikeMysql() {
    assertThat(ProductVariant.optionKey("Đen")).isEqualTo(ProductVariant.optionKey("den"));
    assertThat(ProductVariant.optionKey("Xanh Dương"))
        .isEqualTo(ProductVariant.optionKey("xanh duong"));
    assertThat(ProductVariant.optionKey("Trắng")).isEqualTo(ProductVariant.optionKey("TRANG"));
    assertThat(ProductVariant.optionKey("Giày")).isEqualTo(ProductVariant.optionKey("giay"));
    assertThat(ProductVariant.optionKey("Size L")).isNotEqualTo(ProductVariant.optionKey("Size M"));
    assertThat(ProductVariant.optionKey(null)).isEmpty();
  }

  @Test
  void labelJoinsUsedOptions() {
    assertThat(ProductVariant.labelOf("Đen", "42")).isEqualTo("Đen – 42");
    assertThat(ProductVariant.labelOf("128GB", null)).isEqualTo("128GB");
    assertThat(ProductVariant.labelOf(null, null)).isNull();
  }
}
