package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Recommendation;
import java.util.List;
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
}
