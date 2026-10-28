package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Order;
import com.manhdn.fashionshop.entity.OrderStatus;
import com.manhdn.fashionshop.entity.RefundStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bảng orders. */
public interface OrderRepository extends JpaRepository<Order, Long> {

  /** Điều kiện lọc theo trạng thái hoàn tiền của trang quản trị; tham số null là không lọc. */
  String REFUND_FILTER =
      " and (:refundStatus is null or exists (select r.id from Refund r"
          + " where r.order = o and r.status = :refundStatus))";

  /** Đơn đã tạo bằng mã định danh lần bấm xác nhận này (BR-10). */
  Optional<Order> findByIdempotencyKey(String idempotencyKey);

  /** Đơn theo mã, chỉ khi thuộc tài khoản này. */
  Optional<Order> findByIdAndAccountId(Long id, Long accountId);

  /** Đơn hàng của tài khoản, mới nhất trước (US-11). */
  Page<Order> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId, Pageable pageable);

  /**
   * Đơn hàng cho trang quản trị (US-17), mới nhất trước. status null là mọi trạng thái; keyword
   * null là không lọc, ngược lại khớp đúng mã đơn hoặc một phần email khách, số điện thoại nhận;
   * refundStatus null là không lọc theo hoàn tiền (vd FAILED: đơn cần hoàn tiền thủ công, BR-28).
   */
  @Query(
      value =
          "select o from Order o join fetch o.account a"
              + " where (:status is null or o.status = :status)"
              + " and (:keyword is null or cast(o.id as String) = :keyword"
              + " or a.email like concat('%', :keyword, '%')"
              + " or o.phoneSnapshot like concat('%', :keyword, '%'))"
              + REFUND_FILTER
              + " order by o.createdAt desc, o.id desc",
      countQuery =
          "select count(o) from Order o join o.account a"
              + " where (:status is null or o.status = :status)"
              + " and (:keyword is null or cast(o.id as String) = :keyword"
              + " or a.email like concat('%', :keyword, '%')"
              + " or o.phoneSnapshot like concat('%', :keyword, '%'))"
              + REFUND_FILTER)
  Page<Order> searchForAdmin(
      @Param("status") OrderStatus status,
      @Param("keyword") String keyword,
      @Param("refundStatus") RefundStatus refundStatus,
      Pageable pageable);

  /** Mã các đơn ONLINE còn chờ thanh toán, tạo trước mốc này (BR-34). */
  @Query(
      "select o.id from Order o"
          + " where o.paymentMethod = com.manhdn.fashionshop.entity.PaymentMethod.ONLINE"
          + " and o.paymentStatus = com.manhdn.fashionshop.entity.PaymentStatus.PENDING"
          + " and o.status <> com.manhdn.fashionshop.entity.OrderStatus.CANCELLED"
          + " and o.createdAt < :cutoff order by o.id")
  List<Long> findIdsAwaitingPaymentBefore(@Param("cutoff") LocalDateTime cutoff);

  /** Mã tài khoản đặt đơn, để khóa tài khoản trước khi khóa đơn và biến thể. */
  @Query("select o.account.id from Order o where o.id = :id")
  Optional<Long> findAccountIdById(@Param("id") Long id);

  /** Bỏ tham chiếu tới địa chỉ sắp bị xóa; đơn vẫn giữ bản sao địa chỉ (BR-29). */
  @Modifying(flushAutomatically = true)
  @Query("update Order o set o.addressId = null where o.addressId = :addressId")
  int detachAddress(@Param("addressId") Long addressId);

  /** Số đơn đã đặt, số đơn Đã giao và tổng tiền các đơn Đã giao của một tài khoản (US-18). */
  @Query(
      """
      SELECT COUNT(o) AS orders,
             COALESCE(SUM(CASE WHEN o.status = com.manhdn.fashionshop.entity.OrderStatus.DELIVERED
                          THEN 1 ELSE 0 END), 0) AS delivered,
             COALESCE(SUM(CASE WHEN o.status = com.manhdn.fashionshop.entity.OrderStatus.DELIVERED
                          THEN o.totalAmount ELSE 0 END), 0) AS deliveredAmount
      FROM Order o WHERE o.account.id = :accountId
      """)
  OrderTotals totalsForAccount(@Param("accountId") Long accountId);

  /** Thống kê đơn của một tài khoản. */
  interface OrderTotals {

    /** Số đơn đã đặt (mọi trạng thái). */
    long getOrders();

    /** Số đơn Đã giao. */
    long getDelivered();

    /** Tổng tiền các đơn Đã giao (VNĐ). */
    long getDeliveredAmount();
  }
}
