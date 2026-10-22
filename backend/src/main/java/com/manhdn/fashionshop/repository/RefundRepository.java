package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Refund;
import com.manhdn.fashionshop.entity.RefundStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng refund. */
public interface RefundRepository extends JpaRepository<Refund, Long> {

  /** Lần hoàn tiền của đơn, nếu có. */
  Optional<Refund> findByOrderId(Long orderId);

  /** Mã các lần hoàn tiền ở trạng thái này, cũ nhất trước. */
  List<Refund> findTop20ByStatusOrderByIdAsc(RefundStatus status);
}
