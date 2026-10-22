package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kết quả khảo sát lần đầu của một tài khoản, bảng survey_result (Mục 3.16; US-23, BR-37). Danh
 * mục, thương hiệu và khoảng giá lưu ở {@link UserPreference}.
 */
@Entity
@Table(name = "survey_result")
public class SurveyResult {

  @Id private Long accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SurveyStatus status;

  @Enumerated(EnumType.STRING)
  private ShoppingStyle style;

  @Enumerated(EnumType.STRING)
  private BrandLoyalty brandLoyalty;

  @Column(precision = 2, scale = 1)
  private BigDecimal minRating;

  /** Các câu trả lời đã chọn, dạng JSON. */
  @Column(columnDefinition = "json")
  private String answers;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  /** Constructor rỗng cho JPA. */
  protected SurveyResult() {}

  /** Tạo kết quả của tài khoản với trạng thái cho trước. */
  public SurveyResult(long accountId, SurveyStatus status, LocalDateTime now) {
    this.accountId = accountId;
    this.status = status;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Ghi kết quả của lần làm khảo sát mới nhất (lần đầu hoặc làm lại). */
  public void complete(
      ShoppingStyle style,
      BrandLoyalty brandLoyalty,
      BigDecimal minRating,
      String answers,
      LocalDateTime now) {
    this.status = SurveyStatus.COMPLETED;
    this.style = style;
    this.brandLoyalty = brandLoyalty;
    this.minRating = minRating;
    this.answers = answers;
    this.updatedAt = now;
  }

  public Long getAccountId() {
    return accountId;
  }

  public SurveyStatus getStatus() {
    return status;
  }

  public ShoppingStyle getStyle() {
    return style;
  }

  public BrandLoyalty getBrandLoyalty() {
    return brandLoyalty;
  }

  public BigDecimal getMinRating() {
    return minRating;
  }

  public String getAnswers() {
    return answers;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
