package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.SegmentModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Đọc bảng segment_model (ml-service ghi). */
public interface SegmentModelRepository extends JpaRepository<SegmentModel, Long> {

  /** Lần huấn luyện mới nhất, cũng là lần tạo ra các nhóm hiện có. */
  Optional<SegmentModel> findFirstByOrderByIdDesc();
}
