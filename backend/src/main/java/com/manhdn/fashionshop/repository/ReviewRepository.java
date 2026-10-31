package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.OrderItem;
import com.manhdn.fashionshop.entity.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Truy cập bảng review. */
public interface ReviewRepository extends JpaRepository<Review, Long> {

  /** Đánh giá của tài khoản cho một sản phẩm, nếu có (BR-18: tối đa 1). */
  Optional<Review> findByAccountIdAndProductId(long accountId, long productId);

  /** Các đánh giá của sản phẩm theo trang, nạp sẵn tài khoản và dòng đơn. */
  @EntityGraph(attributePaths = {"account", "orderItem"})
  Page<Review> findByProductId(long productId, Pageable pageable);

  /** Số đánh giá theo từng mức sao của sản phẩm: mỗi phần tử là [số sao, số đánh giá]. */
  @Query(
      "SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :productId"
          + " GROUP BY r.rating")
  List<Object[]> countByRating(long productId);

  /**
   * Các dòng đơn Đã giao của tài khoản có sản phẩm này, đơn mới nhất trước: điều kiện được đánh giá
   * (BR-17).
   */
  @Query(
      "SELECT i FROM OrderItem i JOIN i.order o JOIN i.variant v"
          + " WHERE o.account.id = :accountId AND v.product.id = :productId"
          + " AND o.status = com.manhdn.fashionshop.entity.OrderStatus.DELIVERED"
          + " ORDER BY o.createdAt DESC, i.id DESC")
  List<OrderItem> findDeliveredItems(long accountId, long productId);
}
