package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.AccountSegment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Đọc bảng account_segment (ml-service ghi). */
public interface AccountSegmentRepository extends JpaRepository<AccountSegment, Long> {

  /** Nhóm hiện tại của một tài khoản, nạp sẵn thông tin nhóm. */
  @Query("SELECT a FROM AccountSegment a JOIN FETCH a.segment WHERE a.accountId = :accountId")
  Optional<AccountSegment> findWithSegment(@Param("accountId") Long accountId);

  /** Số khách và R, F, M trung bình của từng nhóm. */
  @Query(
      """
      SELECT a.segment.id AS segmentId, COUNT(a) AS customers, AVG(a.rfmRecency) AS recency,
             AVG(a.rfmFrequency) AS frequency, AVG(a.rfmMonetary) AS monetary
      FROM AccountSegment a GROUP BY a.segment.id
      """)
  List<SegmentStats> statsBySegment();

  /** Số liệu của một nhóm. */
  interface SegmentStats {

    /** Mã dòng customer_segment. */
    Long getSegmentId();

    /** Số khách trong nhóm. */
    long getCustomers();

    /** R trung bình (ngày). */
    Double getRecency();

    /** F trung bình (đơn). */
    Double getFrequency();

    /** M trung bình (VNĐ). */
    Double getMonetary();
  }
}
