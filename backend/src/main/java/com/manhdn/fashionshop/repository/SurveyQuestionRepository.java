package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.SurveyQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng survey_question (chỉ đọc). */
public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {

  /** Mọi câu hỏi kèm đáp án, theo mã câu hỏi. */
  @EntityGraph(attributePaths = "options")
  List<SurveyQuestion> findAllByOrderByIdAsc();
}
