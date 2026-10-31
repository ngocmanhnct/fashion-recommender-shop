package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Voucher;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bảng voucher (mã giảm giá). */
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

  /** Mã có code này (collation của MySQL không phân biệt hoa thường). */
  Optional<Voucher> findByCode(String code);

  /** Đã có mã trùng code chưa. */
  boolean existsByCode(String code);

  /** Đã có mã khác trùng code chưa. */
  boolean existsByCodeAndIdNot(String code, Long id);

  /** Danh sách mã, mới tạo trước; keyword rỗng là mọi mã. */
  @Query(
      "select v from Voucher v where :keyword = '' or v.code like concat('%', :keyword, '%')"
          + " order by v.createdAt desc, v.id desc")
  Page<Voucher> search(@Param("keyword") String keyword, Pageable pageable);

  /**
   * Tăng lượt dùng nếu chưa chạm giới hạn tổng (BR-30); trả 0 nếu mã vừa hết lượt. Chạy trong giao
   * dịch đặt hàng nên rollback cùng đơn.
   */
  @Modifying
  @Query(
      value =
          "UPDATE voucher SET used_count = used_count + 1 WHERE id = :id"
              + " AND (usage_limit_total IS NULL OR used_count < usage_limit_total)",
      nativeQuery = true)
  int tryUse(@Param("id") long id);

  /** Trả lại 1 lượt dùng khi đơn dùng mã bị hủy (BR-33). */
  @Modifying
  @Query(
      value = "UPDATE voucher SET used_count = used_count - 1 WHERE id = :id AND used_count > 0",
      nativeQuery = true)
  int release(@Param("id") long id);
}
