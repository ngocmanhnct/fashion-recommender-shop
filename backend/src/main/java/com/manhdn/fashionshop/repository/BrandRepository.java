package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Brand;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Truy cập bảng brand. */
public interface BrandRepository extends JpaRepository<Brand, Long> {

  /** Tên thương hiệu đã tồn tại chưa (không phân biệt hoa thường theo collation của MySQL). */
  boolean existsByName(String name);

  /** Tìm thương hiệu theo tên (không phân biệt hoa thường và dấu theo collation của MySQL). */
  Optional<Brand> findByName(String name);

  /** Tên đã thuộc về một thương hiệu khác chưa, dùng khi đổi tên. */
  boolean existsByNameAndIdNot(String name, Long id);

  /**
   * Thương hiệu có sản phẩm (chưa xóa) trong các danh mục cho trước, nhiều sản phẩm trước (câu chọn
   * thương hiệu của khảo sát lần đầu).
   */
  @Query(
      "SELECT b FROM Product p JOIN p.brand b"
          + " WHERE p.category.id IN :categoryIds AND p.deletedAt IS NULL"
          + " GROUP BY b ORDER BY COUNT(p) DESC, b.name ASC")
  List<Brand> findTopInCategories(Collection<Long> categoryIds, Pageable pageable);
}
