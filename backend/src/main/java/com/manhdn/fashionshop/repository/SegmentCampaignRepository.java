package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.SegmentCampaign;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn chiến dịch ưu đãi theo nhóm (US-26). */
public interface SegmentCampaignRepository extends JpaRepository<SegmentCampaign, Long> {

  /** Nhóm segmentCode có chương trình tự tặng đang chạy không (mỗi nhóm tối đa 1, US-24). */
  boolean existsBySegmentCodeAndAutoEnabledTrue(String segmentCode);

  /** Chiến dịch mới nhất trước; segmentCode rỗng là mọi nhóm. */
  @EntityGraph(attributePaths = "voucher")
  @Query(
      "select c from SegmentCampaign c where :segmentCode = '' or c.segmentCode = :segmentCode"
          + " order by c.createdAt desc, c.id desc")
  List<SegmentCampaign> findLatest(@Param("segmentCode") String segmentCode, Pageable pageable);

  /** Kết quả bằng đơn hàng của các mã: đơn chưa hủy dùng mã, khách đã mua, doanh thu, tiền giảm. */
  @Query(
      "select new com.manhdn.fashionshop.repository.SegmentCampaignRepository$Outcome("
          + "o.voucher.id, count(o), count(distinct o.account.id), sum(o.totalAmount),"
          + " sum(o.discountAmount))"
          + " from Order o where o.voucher.id in :voucherIds"
          + " and o.status <> com.manhdn.fashionshop.entity.OrderStatus.CANCELLED"
          + " group by o.voucher.id")
  List<Outcome> outcomes(@Param("voucherIds") Collection<Long> voucherIds);

  /**
   * Kết quả của một mã.
   *
   * @param voucherId mã số voucher
   * @param orders số đơn chưa hủy dùng mã
   * @param buyers số khách đã đặt đơn bằng mã
   * @param revenue tổng thanh toán của các đơn đó (VNĐ)
   * @param discount tổng tiền đã giảm (VNĐ)
   */
  record Outcome(Long voucherId, long orders, long buyers, Long revenue, Long discount) {}
}
