package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng product_variant. */
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

  /** Mã SKU đã được dùng chưa. */
  boolean existsBySkuCode(String skuCode);

  /** Mã SKU đã thuộc về một biến thể khác chưa, dùng khi sửa biến thể. */
  boolean existsBySkuCodeAndIdNot(String skuCode, Long id);
}
