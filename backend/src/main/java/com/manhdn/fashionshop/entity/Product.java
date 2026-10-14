package com.manhdn.fashionshop.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Thông tin chung của sản phẩm, bảng product (Mục 3.4), hỗ trợ xóa mềm qua deleted_at. */
@Entity
@Table(name = "product")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "brand_id")
  private Brand brand;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  private LocalDateTime deletedAt;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
  @OrderBy("id")
  private List<ProductVariant> variants = new ArrayList<>();

  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("displayOrder")
  private List<ProductImage> images = new ArrayList<>();

  /** Constructor rỗng cho JPA. */
  protected Product() {}

  /** Tạo sản phẩm mới, chưa có biến thể và ảnh. */
  public Product(Category category, Brand brand, String name, String description) {
    updateInfo(category, brand, name, description);
  }

  /** Cập nhật thông tin chung. */
  public void updateInfo(Category category, Brand brand, String name, String description) {
    this.category = category;
    this.brand = brand;
    this.name = name;
    this.description = description;
  }

  /** Thay toàn bộ danh sách ảnh; thứ tự trong danh sách là thứ tự hiển thị. */
  public void replaceImages(List<String> imageUrls) {
    images.clear();
    for (int i = 0; i < imageUrls.size(); i++) {
      images.add(new ProductImage(this, imageUrls.get(i), i));
    }
  }

  /** Thêm một biến thể vào sản phẩm. */
  public ProductVariant addVariant(
      String size, String color, long price, int stock, String skuCode) {
    ProductVariant variant = new ProductVariant(this, size, color, price, stock, skuCode);
    variants.add(variant);
    return variant;
  }

  /**
   * Xóa mềm: đánh dấu thời điểm ngừng bán và giải phóng mã SKU của mọi biến thể (BR-36). Dòng sản
   * phẩm và biến thể vẫn giữ nguyên để đơn hàng cũ còn tham chiếu được.
   */
  public void softDelete(LocalDateTime now) {
    this.deletedAt = now;
    variants.forEach(ProductVariant::releaseSkuCode);
  }

  /** Sản phẩm đã bị xóa mềm (ngừng bán) hay chưa. */
  public boolean isDeleted() {
    return deletedAt != null;
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

  public Category getCategory() {
    return category;
  }

  public Brand getBrand() {
    return brand;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public List<ProductVariant> getVariants() {
    return variants;
  }

  public List<ProductImage> getImages() {
    return images;
  }
}
