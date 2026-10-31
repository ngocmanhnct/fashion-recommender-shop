package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Một lần tặng mã giảm giá riêng cho một nhóm khách hàng, bảng segment_campaign (US-26). Mã và tên
 * nhóm là bản sao lúc tặng, vì ml-service ghi đè bảng nhóm mỗi lần huấn luyện lại.
 */
@Entity
@Table(name = "segment_campaign")
public class SegmentCampaign {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private String segmentCode;

  @Column(nullable = false, updatable = false)
  private String segmentName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private CampaignTarget target;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "voucher_id", nullable = false, updatable = false)
  private Voucher voucher;

  @Column(nullable = false, updatable = false)
  private int recipientCount;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** Constructor rỗng cho JPA. */
  protected SegmentCampaign() {}

  /** Ghi lại một lần tặng mã. */
  public SegmentCampaign(
      String segmentCode,
      String segmentName,
      CampaignTarget target,
      Voucher voucher,
      int recipientCount,
      LocalDateTime createdAt) {
    this.segmentCode = segmentCode;
    this.segmentName = segmentName;
    this.target = target;
    this.voucher = voucher;
    this.recipientCount = recipientCount;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getSegmentCode() {
    return segmentCode;
  }

  public String getSegmentName() {
    return segmentName;
  }

  public CampaignTarget getTarget() {
    return target;
  }

  public Voucher getVoucher() {
    return voucher;
  }

  public int getRecipientCount() {
    return recipientCount;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
