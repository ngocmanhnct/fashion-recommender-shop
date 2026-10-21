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
 * Một dòng giỏ hàng của tài khoản đã đăng nhập, bảng cart_item (Mục 3.5; US-06, US-07, BR-06). Mỗi
 * tài khoản có tối đa 1 dòng cho mỗi biến thể (UNIQUE account_id, variant_id): thêm lại cùng biến
 * thể thì cộng dồn số lượng.
 */
@Entity
@Table(name = "cart_item")
public class CartItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "variant_id", nullable = false)
  private ProductVariant variant;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  /** Constructor rỗng cho JPA. */
  protected CartItem() {}

  /** Tạo dòng giỏ hàng mới. */
  public CartItem(Account account, ProductVariant variant, int quantity, LocalDateTime updatedAt) {
    this.account = account;
    this.variant = variant;
    this.quantity = quantity;
    this.updatedAt = updatedAt;
  }

  /** Đổi số lượng và ghi lại thời điểm sửa. */
  public void changeQuantity(int quantity, LocalDateTime updatedAt) {
    this.quantity = quantity;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public ProductVariant getVariant() {
    return variant;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
