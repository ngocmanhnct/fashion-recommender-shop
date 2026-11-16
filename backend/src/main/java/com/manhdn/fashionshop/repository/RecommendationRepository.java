package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Recommendation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Đọc bảng recommendation (ml-service ghi). */
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

  /** Danh sách gợi ý của tài khoản theo thứ hạng, nạp sẵn sản phẩm. */
  @Query(
      """
      SELECT r FROM Recommendation r JOIN FETCH r.product
      WHERE r.accountId = :accountId ORDER BY r.rankPosition
      """)
  List<Recommendation> findForAccount(@Param("accountId") Long accountId);

  /**
   * Lần xem sản phẩm hoặc thêm vào giỏ gần nhất của tài khoản từ thời điểm cho trước. Cùng thời
   * điểm thì lấy thêm giỏ (hành động rõ ý hơn).
   */
  @Query(
      value =
          """
          SELECT latest.product_id AS productId, latest.kind AS kind FROM (
            (SELECT product_id, viewed_at AS at, 'VIEW' AS kind FROM product_view_log
             WHERE account_id = :accountId AND viewed_at >= :since
             ORDER BY viewed_at DESC LIMIT 1)
            UNION ALL
            (SELECT v.product_id, c.updated_at AS at, 'CART' AS kind FROM cart_item c
             JOIN product_variant v ON v.id = c.variant_id
             WHERE c.account_id = :accountId AND c.updated_at >= :since
             ORDER BY c.updated_at DESC LIMIT 1)
          ) latest
          ORDER BY latest.at DESC, latest.kind LIMIT 1
          """,
      nativeQuery = true)
  Optional<LatestActivity> findLatestActivity(
      @Param("accountId") Long accountId, @Param("since") LocalDateTime since);

  /**
   * Số yêu cầu tính lại gợi ý của tài khoản còn trong hàng đợi (ml-service chưa tính xong). Trả về
   * {@code long}: MySQL trả COUNT dạng số.
   */
  @Query(
      value =
          "SELECT COUNT(*) FROM recompute_request"
              + " WHERE account_id = :accountId AND task_type = 'RECOMMENDATION'",
      nativeQuery = true)
  long countPendingRecompute(@Param("accountId") Long accountId);

  /** Một dòng của {@link #findLatestActivity}: sản phẩm và loại hành động (VIEW hoặc CART). */
  interface LatestActivity {

    /** Mã sản phẩm. */
    Long getProductId();

    /** VIEW hoặc CART. */
    String getKind();
  }
}
