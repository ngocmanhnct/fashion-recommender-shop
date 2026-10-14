package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bảng product. */
public interface ProductRepository extends JpaRepository<Product, Long> {

  /** Còn sản phẩm nào (kể cả đã xóa mềm) thuộc danh mục này không. */
  boolean existsByCategoryId(Long categoryId);

  /** Còn sản phẩm nào (kể cả đã xóa mềm) thuộc thương hiệu này không. */
  boolean existsByBrandId(Long brandId);

  /**
   * Tìm sản phẩm cho trang quản trị: lọc theo từ khóa trong tên, danh mục, và có lấy cả sản phẩm đã
   * xóa mềm hay không. Tham số null nghĩa là không lọc theo điều kiện đó.
   */
  @Query(
      """
      SELECT p FROM Product p
      WHERE (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:categoryId IS NULL OR p.category.id = :categoryId)
        AND (:includeDeleted = TRUE OR p.deletedAt IS NULL)
      """)
  Page<Product> searchForAdmin(
      @Param("keyword") String keyword,
      @Param("categoryId") Long categoryId,
      @Param("includeDeleted") boolean includeDeleted,
      Pageable pageable);
}
