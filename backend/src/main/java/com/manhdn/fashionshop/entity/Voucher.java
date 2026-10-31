package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Mã giảm giá, bảng voucher (Mục 3.11; US-21, US-22, BR-30 đến BR-33). Thời hạn tính theo giờ Việt
 * Nam. Bộ đếm lượt dùng chỉ đổi bằng UPDATE có điều kiện trong VoucherRepository (an toàn khi nhiều
 * đơn cùng dùng mã), nên entity không bao giờ ghi cột used_count.
 */
@Entity
@Table(name = "voucher")
public class Voucher {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DiscountType discountType;

  @Column(nullable = false)
  private Long discountValue;

  private Long minOrderAmount;

  private Long maxDiscountAmount;

  @Column(nullable = false)
  private LocalDateTime validFrom;

  @Column(nullable = false)
  private LocalDateTime validUntil;

  private Integer usageLimitTotal;

  private Integer usageLimitPerAccount;

  @Column(nullable = false, insertable = false, updatable = false)
  private Integer usedCount;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(nullable = false, updatable = false)
  private boolean recipientsOnly;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** Constructor rỗng cho JPA. */
  protected Voucher() {}

  /** Tạo mã công khai mới đang bật, chưa ai dùng. */
  public Voucher(String code, LocalDateTime createdAt) {
    this(code, createdAt, false);
  }

  /**
   * Tạo mã mới đang bật, chưa ai dùng.
   *
   * @param recipientsOnly true nếu chỉ khách được tặng dùng được (BR-44)
   */
  public Voucher(String code, LocalDateTime createdAt, boolean recipientsOnly) {
    this.recipientsOnly = recipientsOnly;
    this.code = code;
    this.active = true;
    this.usedCount = 0;
    this.createdAt = createdAt;
  }

  /** Đặt các điều kiện của mã (loại, giá trị, đơn tối thiểu, trần giảm, thời hạn, giới hạn). */
  public void configure(
      DiscountType discountType,
      long discountValue,
      Long minOrderAmount,
      Long maxDiscountAmount,
      LocalDateTime validFrom,
      LocalDateTime validUntil,
      Integer usageLimitTotal,
      Integer usageLimitPerAccount) {
    this.discountType = discountType;
    this.discountValue = discountValue;
    this.minOrderAmount = minOrderAmount;
    this.maxDiscountAmount = maxDiscountAmount;
    this.validFrom = validFrom;
    this.validUntil = validUntil;
    this.usageLimitTotal = usageLimitTotal;
    this.usageLimitPerAccount = usageLimitPerAccount;
  }

  /**
   * Số tiền giảm cho tổng tiền hàng subtotal theo loại và giá trị của mã, chưa tính mức sàn của
   * BR-32: phần trăm thì làm tròn xuống đến đồng và không quá trần giảm.
   */
  public long rawDiscountFor(long subtotal) {
    if (discountType == DiscountType.FIXED) {
      return discountValue;
    }
    long discount = subtotal * discountValue / 100;
    return maxDiscountAmount == null ? discount : Math.min(discount, maxDiscountAmount);
  }

  /** Còn lượt dùng tổng không (BR-30); null giới hạn là không giới hạn. */
  public boolean hasTotalUsesLeft() {
    return usageLimitTotal == null || getUsedCount() < usageLimitTotal;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public DiscountType getDiscountType() {
    return discountType;
  }

  public long getDiscountValue() {
    return discountValue;
  }

  public Long getMinOrderAmount() {
    return minOrderAmount;
  }

  public Long getMaxDiscountAmount() {
    return maxDiscountAmount;
  }

  public LocalDateTime getValidFrom() {
    return validFrom;
  }

  public LocalDateTime getValidUntil() {
    return validUntil;
  }

  public Integer getUsageLimitTotal() {
    return usageLimitTotal;
  }

  public Integer getUsageLimitPerAccount() {
    return usageLimitPerAccount;
  }

  public int getUsedCount() {
    return usedCount == null ? 0 : usedCount;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public boolean isRecipientsOnly() {
    return recipientsOnly;
  }
}
