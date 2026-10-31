package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.CustomerSegment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Đọc bảng customer_segment (ml-service ghi). */
public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, Long> {

  /** Nhóm theo mã (mã là duy nhất, uk_customer_segment_code). */
  Optional<CustomerSegment> findByCode(String code);
}
