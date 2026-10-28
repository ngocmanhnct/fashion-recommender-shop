package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.CustomerSegment;
import org.springframework.data.jpa.repository.JpaRepository;

/** Đọc bảng customer_segment (ml-service ghi). */
public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, Long> {}
