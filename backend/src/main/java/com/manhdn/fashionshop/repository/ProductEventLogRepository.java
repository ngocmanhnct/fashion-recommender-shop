package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.ProductEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng product_event_log. */
public interface ProductEventLogRepository extends JpaRepository<ProductEventLog, Long> {}
