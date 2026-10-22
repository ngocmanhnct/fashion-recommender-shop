package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Sở thích lấy từ khảo sát lần đầu, bảng user_preference (Mục 3.12; US-23, BR-38): một dòng cho mỗi
 * danh mục quan tâm, cùng thương hiệu và khoảng giá cho mọi dòng của tài khoản.
 */
@Entity
@Table(name = "user_preference")
public class UserPreference {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "brand_id")
  private Brand brand;

  @Column(nullable = false)
  private long priceMin;

  private Long priceMax;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  /** Constructor rỗng cho JPA. */
  protected UserPreference() {}

  /** Tạo sở thích cho một danh mục; trần giá null nghĩa là không giới hạn trên. */
  public UserPreference(
      Account account,
      Category category,
      Brand brand,
      long priceMin,
      Long priceMax,
      LocalDateTime now) {
    this.account = account;
    this.category = category;
    this.brand = brand;
    this.priceMin = priceMin;
    this.priceMax = priceMax;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public Category getCategory() {
    return category;
  }

  public Brand getBrand() {
    return brand;
  }

  public long getPriceMin() {
    return priceMin;
  }

  public Long getPriceMax() {
    return priceMax;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
