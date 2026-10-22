package com.manhdn.fashionshop.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Một đáp án của câu hỏi khảo sát, bảng survey_option (Mục 3.16; US-23, BR-41). Đáp án trỏ tới câu
 * tiếp theo (null = kết thúc khảo sát) và mang phần thông tin nó đóng góp vào hồ sơ khách; trường
 * null nghĩa là đáp án không đổi thông tin đó.
 */
@Entity
@Table(name = "survey_option")
public class SurveyOption {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private SurveyQuestion question;

  @Column(nullable = false, length = 20)
  private String code;

  @Column(nullable = false, length = 200)
  private String label;

  @Column(nullable = false)
  private int displayOrder;

  private Long nextQuestionId;

  @Enumerated(EnumType.STRING)
  private SurveyPurpose purpose;

  @Enumerated(EnumType.STRING)
  private Gender gender;

  @Enumerated(EnumType.STRING)
  private ShoppingStyle style;

  private Long priceMin;

  private Long priceMax;

  private Boolean priceFlexible;

  @Enumerated(EnumType.STRING)
  private BrandLoyalty brandLoyalty;

  @Column(precision = 2, scale = 1)
  private BigDecimal minRating;

  /** Tên các danh mục ứng với đáp án dịp sử dụng, bảng survey_option_category. */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "survey_option_category", joinColumns = @JoinColumn(name = "option_id"))
  @Column(name = "category_name", nullable = false, length = 100)
  private Set<String> categoryNames = new HashSet<>();

  /** Constructor rỗng cho JPA. */
  protected SurveyOption() {}

  public Long getId() {
    return id;
  }

  public SurveyQuestion getQuestion() {
    return question;
  }

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public Long getNextQuestionId() {
    return nextQuestionId;
  }

  public SurveyPurpose getPurpose() {
    return purpose;
  }

  public Gender getGender() {
    return gender;
  }

  public ShoppingStyle getStyle() {
    return style;
  }

  public Long getPriceMin() {
    return priceMin;
  }

  public Long getPriceMax() {
    return priceMax;
  }

  public Boolean getPriceFlexible() {
    return priceFlexible;
  }

  public BrandLoyalty getBrandLoyalty() {
    return brandLoyalty;
  }

  public BigDecimal getMinRating() {
    return minRating;
  }

  public Set<String> getCategoryNames() {
    return categoryNames;
  }
}
