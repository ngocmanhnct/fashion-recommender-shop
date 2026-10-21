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
 * Một lượt xem sản phẩm của tài khoản đã đăng nhập, bảng product_view_log (Mục 3.7; US-03, BR-19).
 * Mọi lượt xem đều được ghi, kể cả xem lại cùng sản phẩm; ngưỡng BR-19 đếm số sản phẩm khác nhau.
 */
@Entity
@Table(name = "product_view_log")
public class ProductViewLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(nullable = false)
  private LocalDateTime viewedAt;

  /** Constructor rỗng cho JPA. */
  protected ProductViewLog() {}

  /** Tạo lượt xem của tài khoản với sản phẩm tại thời điểm cho trước (giờ của JVM backend). */
  public ProductViewLog(Account account, Product product, LocalDateTime viewedAt) {
    this.account = account;
    this.product = product;
    this.viewedAt = viewedAt;
  }

  public Long getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public Product getProduct() {
    return product;
  }

  public LocalDateTime getViewedAt() {
    return viewedAt;
  }
}
