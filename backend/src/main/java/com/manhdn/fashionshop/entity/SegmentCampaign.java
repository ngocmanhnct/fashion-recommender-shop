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
 * Một lần tặng mã giảm giá riêng cho một nhóm khách hàng, bảng segment_campaign (US-24). Mã và tên
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

  @Column(updatable = false)
  private Integer validDays;

  @Column(nullable = false)
  private boolean autoEnabled;

  @Column(nullable = false, updatable = false)
  private int holdoutPercent;

  @Column(updatable = false)
  private Integer maxRecipients;

  @Column(updatable = false)
  private Integer grossMarginPercent;

  @Column(nullable = false, updatable = false)
  private int recipientCount;

  @Column(nullable = false, updatable = false)
  private int holdoutCount;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** Constructor rỗng cho JPA. */
  protected SegmentCampaign() {}

  /** Ghi lại một lần tặng mã cho cả nhóm hoặc khách cần chăm sóc. */
  public SegmentCampaign(
      String segmentCode,
      String segmentName,
      CampaignTarget target,
      Voucher voucher,
      int recipientCount,
      LocalDateTime createdAt) {
    this(segmentCode, segmentName, target, voucher, recipientCount, createdAt, null);
  }

  /**
   * Ghi lại một chiến dịch; validDays khác null là chương trình tự tặng (US-24), bật ngay: mỗi
   * khách dùng mã trong validDays ngày kể từ khi nhận.
   */
  public SegmentCampaign(
      String segmentCode,
      String segmentName,
      CampaignTarget target,
      Voucher voucher,
      int recipientCount,
      LocalDateTime createdAt,
      Integer validDays) {
    this.segmentCode = segmentCode;
    this.segmentName = segmentName;
    this.target = target;
    this.voucher = voucher;
    this.recipientCount = recipientCount;
    this.createdAt = createdAt;
    this.validDays = validDays;
    this.autoEnabled = validDays != null;
  }

  /**
   * Đặt nhóm đối chứng và cách tính lãi lúc tạo (rà soát 12/11/2026, việc 3 và 4).
   *
   * @param holdoutPercent phần trăm khách đủ điều kiện bị giữ lại, không nhận mã
   * @param holdoutCount số khách đã bị giữ lại
   * @param maxRecipients trần số khách của chương trình tự tặng, null là không giới hạn
   * @param grossMarginPercent lãi gộp trên tiền hàng (%), null là không tính lãi
   * @return chính chiến dịch này
   */
  public SegmentCampaign withMeasurement(
      int holdoutPercent, int holdoutCount, Integer maxRecipients, Integer grossMarginPercent) {
    this.holdoutPercent = holdoutPercent;
    this.holdoutCount = holdoutCount;
    this.maxRecipients = maxRecipients;
    this.grossMarginPercent = grossMarginPercent;
    return this;
  }

  /** Dừng tự tặng: khách vào nhóm sau đó không nhận mã nữa, khách đã nhận vẫn dùng được. */
  public void stopAuto() {
    this.autoEnabled = false;
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

  public int getHoldoutCount() {
    return holdoutCount;
  }

  public int getHoldoutPercent() {
    return holdoutPercent;
  }

  public Integer getMaxRecipients() {
    return maxRecipients;
  }

  public Integer getGrossMarginPercent() {
    return grossMarginPercent;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public Integer getValidDays() {
    return validDays;
  }

  public boolean isAutoEnabled() {
    return autoEnabled;
  }
}
