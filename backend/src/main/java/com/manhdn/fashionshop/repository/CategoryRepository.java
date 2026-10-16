package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng category. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

  /** Tên danh mục đã tồn tại chưa (không phân biệt hoa thường theo collation của MySQL). */
  boolean existsByName(String name);

  /** Tìm danh mục theo tên (không phân biệt hoa thường và dấu theo collation của MySQL). */
  Optional<Category> findByName(String name);

  /** Tên đã thuộc về một danh mục khác chưa, dùng khi đổi tên. */
  boolean existsByNameAndIdNot(String name, Long id);
}
