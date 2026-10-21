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
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Biến thể sản phẩm, bảng product_variant (Mục 3.4): một tổ hợp giá trị phân loại (vd Đen – 42) là
 * một SKU có giá và tồn kho riêng (BR-05). Giá trị ở tầng sản phẩm không dùng thì là null; sản phẩm
 * không có phân loại có đúng 1 biến thể, cả 2 giá trị đều null.
 */
@Entity
@Table(name = "product_variant")
public class ProductVariant {

  /** Dấu thanh và dấu mũ sau khi tách chữ theo chuẩn NFD (vd "ắ" thành "a" + 2 dấu). */
  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(length = 100)
  private String option1Value;

  @Column(length = 100)
  private String option2Value;

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
      Product product,
      String option1Value,
      String option2Value,
      long price,
      int stock,
      String skuCode) {
    this.product = product;
    update(option1Value, option2Value, price, stock, skuCode);
  }

  /** Cập nhật toàn bộ thuộc tính của biến thể. */
  public void update(
      String option1Value, String option2Value, long price, int stock, String skuCode) {
    this.option1Value = option1Value;
    this.option2Value = option2Value;
    this.price = price;
    this.stock = stock;
    this.skuCode = skuCode;
  }

  /**
   * Trừ tồn kho khi đơn hàng được xác nhận (BR-14). Service phải khóa dòng biến thể và kiểm tra đủ
   * tồn kho trước (BR-08).
   */
  public void decreaseStock(int quantity) {
    if (quantity > stock) {
      throw new IllegalStateException("Tồn kho không đủ để trừ");
    }
    this.stock -= quantity;
  }

  /** Hoàn lại tồn kho khi đơn hàng bị hủy (BR-16). */
  public void increaseStock(int quantity) {
    this.stock += quantity;
  }

  /** Giải phóng mã SKU khi sản phẩm bị xóa mềm, để mã được dùng lại (BR-36). */
  public void releaseSkuCode() {
    this.skuCode = null;
  }

  /** Biến thể có cùng tổ hợp giá trị phân loại (so như {@link #optionKey}) hay không. */
  public boolean sameOptionAs(String otherOption1, String otherOption2) {
    return optionKey(option1Value).equals(optionKey(otherOption1))
        && optionKey(option2Value).equals(optionKey(otherOption2));
  }

  /** Nhãn phân loại để hiển thị và lưu bản sao ở dòng đơn (BR-15), vd "Đen – 42". */
  public String label() {
    return labelOf(option1Value, option2Value);
  }

  /**
   * Nhãn của một tổ hợp giá trị phân loại: các giá trị khác null nối bằng " – "; null nếu sản phẩm
   * không có phân loại.
   */
  public static String labelOf(String option1Value, String option2Value) {
    if (option1Value == null) {
      return null;
    }
    return option2Value == null ? option1Value : option1Value + " – " + option2Value;
  }

  /**
   * Khóa để so 2 giá trị phân loại giống collation mặc định của MySQL (utf8mb4_0900_ai_ci) mà chỉ
   * mục uk_product_variant_options dùng: không phân biệt hoa thường và dấu ("Đen" trùng "den");
   * null thành chuỗi rỗng như COALESCE trong chỉ mục.
   */
  public static String optionKey(String value) {
    if (value == null) {
      return "";
    }
    String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
    String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
    return withoutMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
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

  public String getOption1Value() {
    return option1Value;
  }

  public String getOption2Value() {
    return option2Value;
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
