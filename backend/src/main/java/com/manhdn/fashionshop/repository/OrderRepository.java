package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bảng orders. */
public interface OrderRepository extends JpaRepository<Order, Long> {

  /** Đơn đã tạo bằng mã định danh lần bấm xác nhận này (BR-10). */
  Optional<Order> findByIdempotencyKey(String idempotencyKey);

  /** Đơn theo mã, chỉ khi thuộc tài khoản này. */
  Optional<Order> findByIdAndAccountId(Long id, Long accountId);

  /** Đơn hàng của tài khoản, mới nhất trước (US-11). */
  Page<Order> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId, Pageable pageable);

  /** Bỏ tham chiếu tới địa chỉ sắp bị xóa; đơn vẫn giữ bản sao địa chỉ (BR-29). */
  @Modifying(flushAutomatically = true)
  @Query("update Order o set o.addressId = null where o.addressId = :addressId")
  int detachAddress(@Param("addressId") Long addressId);
}
