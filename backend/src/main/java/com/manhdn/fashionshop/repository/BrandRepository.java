package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Brand;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng brand. */
public interface BrandRepository extends JpaRepository<Brand, Long> {

  /** Tên thương hiệu đã tồn tại chưa (không phân biệt hoa thường theo collation của MySQL). */
  boolean existsByName(String name);

  /** Tìm thương hiệu theo tên (không phân biệt hoa thường và dấu theo collation của MySQL). */
  Optional<Brand> findByName(String name);

  /** Tên đã thuộc về một thương hiệu khác chưa, dùng khi đổi tên. */
  boolean existsByNameAndIdNot(String name, Long id);
}
