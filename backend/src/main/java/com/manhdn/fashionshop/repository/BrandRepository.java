package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng brand. */
public interface BrandRepository extends JpaRepository<Brand, Long> {

  /** Tên thương hiệu đã tồn tại chưa (không phân biệt hoa thường theo collation của MySQL). */
  boolean existsByName(String name);

  /** Tên đã thuộc về một thương hiệu khác chưa, dùng khi đổi tên. */
  boolean existsByNameAndIdNot(String name, Long id);
}
