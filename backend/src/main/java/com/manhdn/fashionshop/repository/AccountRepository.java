package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bảng account. */
public interface AccountRepository extends JpaRepository<Account, Long> {

  /** Tìm tài khoản theo email (email đã chuẩn hóa chữ thường). */
  Optional<Account> findByEmail(String email);

  /** Kiểm tra email đã có tài khoản chưa (BR-01). */
  boolean existsByEmail(String email);

  /**
   * Đọc tài khoản và khóa dòng (SELECT ... FOR UPDATE) đến hết giao dịch, để các thao tác đồng thời
   * của cùng một tài khoản (vd 2 lượt xem cùng lúc) chạy lần lượt.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Account> findWithLockById(Long id);

  /** Số tài khoản có vai trò cho trước. */
  long countByRole(Role role);

  /**
   * Danh sách khách hàng cho trang quản trị (US-18), mới đăng ký trước. keyword null là không lọc,
   * ngược lại khớp một phần họ tên hoặc email; segment null là mọi nhóm, ngược lại đúng mã nhóm;
   * unsegmented true là chỉ khách chưa được phân nhóm (BR-24).
   */
  @Query(
      value =
          """
          SELECT a.id AS id, a.fullName AS fullName, a.email AS email, a.createdAt AS createdAt,
                 (SELECT COUNT(o) FROM Order o WHERE o.account = a) AS orderCount,
                 s.code AS segmentCode, s.name AS segmentName
          FROM Account a LEFT JOIN AccountSegment seg ON seg.accountId = a.id
               LEFT JOIN seg.segment s
          WHERE a.role = com.manhdn.fashionshop.entity.Role.CUSTOMER
            AND (:keyword IS NULL OR a.email LIKE CONCAT('%', :keyword, '%')
                 OR a.fullName LIKE CONCAT('%', :keyword, '%'))
            AND (:segment IS NULL OR s.code = :segment)
            AND (:unsegmented = FALSE OR seg.accountId IS NULL)
          ORDER BY a.createdAt DESC, a.id DESC
          """,
      countQuery =
          """
          SELECT COUNT(a)
          FROM Account a LEFT JOIN AccountSegment seg ON seg.accountId = a.id
               LEFT JOIN seg.segment s
          WHERE a.role = com.manhdn.fashionshop.entity.Role.CUSTOMER
            AND (:keyword IS NULL OR a.email LIKE CONCAT('%', :keyword, '%')
                 OR a.fullName LIKE CONCAT('%', :keyword, '%'))
            AND (:segment IS NULL OR s.code = :segment)
            AND (:unsegmented = FALSE OR seg.accountId IS NULL)
          """)
  Page<CustomerRow> searchCustomers(
      @Param("keyword") String keyword,
      @Param("segment") String segment,
      @Param("unsegmented") boolean unsegmented,
      Pageable pageable);

  /** Một dòng của danh sách khách hàng. */
  interface CustomerRow {

    /** Mã tài khoản. */
    Long getId();

    /** Họ tên. */
    String getFullName();

    /** Email. */
    String getEmail();

    /** Ngày đăng ký. */
    LocalDateTime getCreatedAt();

    /** Số đơn đã đặt (mọi trạng thái). */
    long getOrderCount();

    /** Mã nhóm hiện tại, null nếu chưa được phân nhóm. */
    String getSegmentCode();

    /** Tên nhóm hiện tại, null nếu chưa được phân nhóm. */
    String getSegmentName();
  }
}
