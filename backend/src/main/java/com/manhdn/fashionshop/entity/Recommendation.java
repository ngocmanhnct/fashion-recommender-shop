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
 * Một sản phẩm trong danh sách gợi ý trang chủ của một tài khoản, bảng recommendation (Mục 3.13;
 * US-13, BR-20). Chỉ ml-service ghi bảng này; backend chỉ đọc.
 */
@Entity
@Immutable
@Table(name = "recommendation")
public class Recommendation {

  @Id private Long id;

  @Column(nullable = false)
  private Long accountId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  /** Thứ hạng, bắt đầu từ 1; cột rank_position vì RANK là từ khóa của MySQL 8. */
  @Column(name = "rank_position", nullable = false)
  private Integer rankPosition;

  @Column(nullable = false)
  private Double score;

  @Column(nullable = false)
  private String reason;

  @Column(nullable = false)
  private LocalDateTime generatedAt;

  /** Constructor rỗng cho JPA. */
  protected Recommendation() {}

  public Long getId() {
    return id;
  }

  public Long getAccountId() {
    return accountId;
  }

  public Product getProduct() {
    return product;
  }

  public Integer getRankPosition() {
    return rankPosition;
  }

  public Double getScore() {
    return score;
  }

  public String getReason() {
    return reason;
  }

  public LocalDateTime getGeneratedAt() {
    return generatedAt;
  }
}
