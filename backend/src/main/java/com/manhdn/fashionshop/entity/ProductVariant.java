package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Biến thể sản phẩm, bảng product_variant (Mục 3.4): một tổ hợp size × màu là một SKU có giá và tồn
 * kho riêng (BR-05).
 */
@Entity
@Table(name = "product_variant")
public class ProductVariant {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(nullable = false, length = 20)
  private String size;

  @Column(nullable = false, length = 50)
  private String color;

  @Column(nullable = false)
  private Long price;

  @Column(nullable = false)
  private Integer stock;

  @Column(unique = true, length = 50)
  private String skuCode;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  /** Constructor rỗng cho JPA. */
  protected ProductVariant() {}

  /** Tạo biến thể gắn với sản phẩm. */
  public ProductVariant(
      Product product, String size, String color, long price, int stock, String skuCode) {
    this.product = product;
    update(size, color, price, stock, skuCode);
  }

  /** Cập nhật toàn bộ thuộc tính của biến thể. */
  public void update(String size, String color, long price, int stock, String skuCode) {
    this.size = size;
    this.color = color;
    this.price = price;
    this.stock = stock;
    this.skuCode = skuCode;
  }

  /** Giải phóng mã SKU khi sản phẩm bị xóa mềm, để mã được dùng lại (BR-36). */
  public void releaseSkuCode() {
    this.skuCode = null;
  }

  /** Biến thể có cùng tổ hợp size × màu (không phân biệt hoa thường) hay không. */
  public boolean sameOptionAs(String otherSize, String otherColor) {
    return size.equalsIgnoreCase(otherSize) && color.equalsIgnoreCase(otherColor);
  }

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Product getProduct() {
    return product;
  }

  public String getSize() {
    return size;
  }

  public String getColor() {
    return color;
  }

  public Long getPrice() {
    return price;
  }

  public Integer getStock() {
    return stock;
  }

  public String getSkuCode() {
    return skuCode;
  }
}
