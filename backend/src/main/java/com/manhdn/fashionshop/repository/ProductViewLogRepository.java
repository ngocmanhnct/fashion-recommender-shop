package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.ProductViewLog;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bảng product_view_log, phục vụ ngưỡng "đã xem ≥5 sản phẩm trong 30 ngày" (BR-19). */
public interface ProductViewLogRepository extends JpaRepository<ProductViewLog, Long> {

  /** Tài khoản đã xem sản phẩm này từ thời điểm cho trước chưa. */
  boolean existsByAccountIdAndProductIdAndViewedAtGreaterThanEqual(
      Long accountId, Long productId, LocalDateTime since);

  /** Số sản phẩm khác nhau tài khoản đã xem từ thời điểm cho trước, kể cả sản phẩm đã xóa mềm. */
  @Query(
      """
      SELECT COUNT(DISTINCT v.product.id) FROM ProductViewLog v
      WHERE v.account.id = :accountId AND v.viewedAt >= :since
      """)
  long countDistinctProductsSince(
      @Param("accountId") Long accountId, @Param("since") LocalDateTime since);

  /**
   * Số đơn đã hoàn tất (DELIVERED) của tài khoản, vế còn lại của ngưỡng BR-19. Viết bằng SQL vì
   * chưa có entity đơn hàng; chuyển sang repository của đơn hàng khi làm phần đặt hàng. Trả về
   * {@code long} chứ không {@code boolean}: MySQL trả COUNT/EXISTS dạng số.
   */
  @Query(
      value = "SELECT COUNT(*) FROM orders WHERE account_id = :accountId AND status = 'DELIVERED'",
      nativeQuery = true)
  long countDeliveredOrders(@Param("accountId") Long accountId);
}
