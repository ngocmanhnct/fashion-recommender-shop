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

/** Ảnh sản phẩm theo thứ tự hiển thị, bảng product_image (Mục 3.4). */
@Entity
@Table(name = "product_image")
public class ProductImage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(nullable = false, length = 500)
  private String imageUrl;

  @Column(nullable = false)
  private Integer displayOrder;

  /** Constructor rỗng cho JPA. */
  protected ProductImage() {}

  /** Tạo ảnh gắn với sản phẩm, ở vị trí hiển thị cho trước (0 = ảnh đầu tiên). */
  public ProductImage(Product product, String imageUrl, int displayOrder) {
    this.product = product;
    this.imageUrl = imageUrl;
    this.displayOrder = displayOrder;
  }

  public Long getId() {
    return id;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public Integer getDisplayOrder() {
    return displayOrder;
  }
}
