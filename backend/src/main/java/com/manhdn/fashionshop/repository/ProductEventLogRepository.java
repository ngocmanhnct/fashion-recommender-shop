package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.ProductEventLog;
import com.manhdn.fashionshop.entity.ProductEventType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bảng product_event_log. */
public interface ProductEventLogRepository extends JpaRepository<ProductEventLog, Long> {

  /** Số sự kiện theo vị trí, nguồn và loại trong khoảng [from, to). */
  @Query(
      """
      SELECT e.placement AS placement, e.source AS source, e.eventType AS eventType,
             COUNT(e) AS total
      FROM ProductEventLog e
      WHERE e.eventType IN :types AND e.createdAt >= :from AND e.createdAt < :to
      GROUP BY e.placement, e.source, e.eventType
      """)
  List<EventCount> countByGroup(
      @Param("types") Collection<ProductEventType> types,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  /** Một dòng kết quả của {@link #countByGroup}. */
  interface EventCount {
    /** Vị trí khối gợi ý. */
    String getPlacement();

    /** Nguồn gợi ý. */
    String getSource();

    /** Loại sự kiện. */
    ProductEventType getEventType();

    /** Số sự kiện. */
    long getTotal();
  }
}
