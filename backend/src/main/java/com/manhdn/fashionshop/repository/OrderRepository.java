package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Order;
import com.manhdn.fashionshop.entity.OrderStatus;
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

  /**
   * Đơn hàng cho trang quản trị (US-17), mới nhất trước. status null là mọi trạng thái; keyword
   * null là không lọc, ngược lại khớp đúng mã đơn hoặc một phần email khách, số điện thoại nhận.
   */
  @Query(
      value =
          "select o from Order o join fetch o.account a"
              + " where (:status is null or o.status = :status)"
              + " and (:keyword is null or cast(o.id as String) = :keyword"
              + " or a.email like concat('%', :keyword, '%')"
              + " or o.phoneSnapshot like concat('%', :keyword, '%'))"
              + " order by o.createdAt desc, o.id desc",
      countQuery =
          "select count(o) from Order o join o.account a"
              + " where (:status is null or o.status = :status)"
              + " and (:keyword is null or cast(o.id as String) = :keyword"
              + " or a.email like concat('%', :keyword, '%')"
              + " or o.phoneSnapshot like concat('%', :keyword, '%'))")
  Page<Order> searchForAdmin(
      @Param("status") OrderStatus status, @Param("keyword") String keyword, Pageable pageable);

  /** Mã tài khoản đặt đơn, để khóa tài khoản trước khi khóa đơn và biến thể. */
  @Query("select o.account.id from Order o where o.id = :id")
  Optional<Long> findAccountIdById(@Param("id") Long id);

  /** Bỏ tham chiếu tới địa chỉ sắp bị xóa; đơn vẫn giữ bản sao địa chỉ (BR-29). */
  @Modifying(flushAutomatically = true)
  @Query("update Order o set o.addressId = null where o.addressId = :addressId")
  int detachAddress(@Param("addressId") Long addressId);
}
