package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Nhóm hiện tại của một khách hàng và R, F, M lúc xếp nhóm, bảng account_segment (Mục 3.10; BR-23,
 * BR-24). Khách chưa có đơn Đã giao không có dòng. Chỉ ml-service ghi; backend chỉ đọc.
 */
@Entity
@Immutable
@Table(name = "account_segment")
public class AccountSegment {

  @Id private Long accountId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "segment_id", nullable = false)
  private CustomerSegment segment;

  /** R: số ngày từ lúc đặt đơn Đã giao gần nhất. */
  @Column(nullable = false)
  private Integer rfmRecency;

  /** F: số đơn Đã giao. */
  @Column(nullable = false)
  private Integer rfmFrequency;

  /** M: tổng tiền các đơn Đã giao (VNĐ). */
  @Column(nullable = false)
  private Long rfmMonetary;

  @Column(nullable = false)
  private LocalDateTime computedAt;

  /** Constructor rỗng cho JPA. */
  protected AccountSegment() {}

  public Long getAccountId() {
    return accountId;
  }

  public CustomerSegment getSegment() {
    return segment;
  }

  public Integer getRfmRecency() {
    return rfmRecency;
  }

  public Integer getRfmFrequency() {
    return rfmFrequency;
  }

  public Long getRfmMonetary() {
    return rfmMonetary;
  }

  public LocalDateTime getComputedAt() {
    return computedAt;
  }
}
