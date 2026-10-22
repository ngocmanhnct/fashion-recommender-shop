package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Một câu hỏi của khảo sát lần đầu dạng rẽ nhánh, bảng survey_question (Mục 3.16; US-23, BR-41).
 * Nội dung nạp sẵn bằng migration V5, ứng dụng chỉ đọc.
 */
@Entity
@Table(name = "survey_question")
public class SurveyQuestion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 10)
  private String code;

  @Column(nullable = false)
  private String content;

  private String hint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SurveyAnswerType answerType;

  @Column(nullable = false)
  private int maxChoices;

  /** Câu tiếp theo của câu chọn thương hiệu (đáp án của câu đó không lưu trong bảng). */
  private Long nextQuestionId;

  @OneToMany(mappedBy = "question")
  @OrderBy("displayOrder ASC")
  private List<SurveyOption> options = new ArrayList<>();

  /** Constructor rỗng cho JPA. */
  protected SurveyQuestion() {}

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getContent() {
    return content;
  }

  public String getHint() {
    return hint;
  }

  public SurveyAnswerType getAnswerType() {
    return answerType;
  }

  public int getMaxChoices() {
    return maxChoices;
  }

  public Long getNextQuestionId() {
    return nextQuestionId;
  }

  public List<SurveyOption> getOptions() {
    return options;
  }
}
