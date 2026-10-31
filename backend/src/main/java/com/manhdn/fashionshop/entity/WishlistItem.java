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
 * Một sản phẩm trong danh sách yêu thích của tài khoản, bảng wishlist_item (Mục 3.9; US-14). Ghi ở
 * cấp sản phẩm, mỗi tài khoản có tối đa 1 dòng cho mỗi sản phẩm (UNIQUE account_id, product_id).
 */
@Entity
@Table(name = "wishlist_item")
public class WishlistItem {

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
  private LocalDateTime createdAt;

  /** Constructor rỗng cho JPA. */
  protected WishlistItem() {}

  /** Thêm sản phẩm vào danh sách yêu thích của tài khoản. */
  public WishlistItem(Account account, Product product, LocalDateTime createdAt) {
    this.account = account;
    this.product = product;
    this.createdAt = createdAt;
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

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
