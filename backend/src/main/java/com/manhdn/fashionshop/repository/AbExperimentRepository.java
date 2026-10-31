package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.AbExperiment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng ab_experiment (US-27). */
public interface AbExperimentRepository extends JpaRepository<AbExperiment, Long> {

  /** Thử nghiệm đang chạy, nếu có (nhiều nhất 1). */
  Optional<AbExperiment> findFirstByEndedAtIsNullOrderByIdDesc();

  /** Mọi thử nghiệm, mới nhất trước. */
  List<AbExperiment> findAllByOrderByIdDesc();
}
