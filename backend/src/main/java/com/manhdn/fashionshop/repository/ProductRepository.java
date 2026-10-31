package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Product;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy cập bảng product. Trang người dùng lọc bằng {@link ProductSpecifications}; gợi ý khi tìm
 * không thấy dùng truy vấn riêng ở {@link ProductSuggestionRepository}.
 */
public interface ProductRepository
    extends JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product>,
        ProductSuggestionRepository {

  /** Còn sản phẩm nào (kể cả đã xóa mềm) thuộc danh mục này không. */
  boolean existsByCategoryId(Long categoryId);

  /** Còn sản phẩm nào (kể cả đã xóa mềm) thuộc thương hiệu này không. */
  boolean existsByBrandId(Long brandId);

  /** Sản phẩm có tồn tại và đang bán (chưa xóa mềm) không. */
  boolean existsByIdAndDeletedAtIsNull(Long id);

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

  /**
   * Sản phẩm hay nằm chung đơn (khác CANCELLED) với các sản phẩm cho trước, đơn chung nhiều nhất
   * trước; dùng cho "Thường được mua cùng" ở giỏ hàng khi không gọi được ml-service.
   */
  @Query(
      value =
          """
          SELECT v2.product_id
          FROM order_item i1
          JOIN product_variant v1 ON v1.id = i1.variant_id
          JOIN orders o ON o.id = i1.order_id AND o.status <> 'CANCELLED'
          JOIN order_item i2 ON i2.order_id = o.id
          JOIN product_variant v2 ON v2.id = i2.variant_id
          WHERE v1.product_id IN (:ids) AND v2.product_id NOT IN (:ids)
          GROUP BY v2.product_id
          ORDER BY COUNT(DISTINCT o.id) DESC, v2.product_id
          LIMIT :limit
          """,
      nativeQuery = true)
  List<Long> findBoughtTogether(@Param("ids") Collection<Long> ids, @Param("limit") int limit);
}
