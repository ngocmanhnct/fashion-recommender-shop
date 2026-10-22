package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Câu trả lời cho một câu hỏi khảo sát lần đầu (US-23).
 *
 * @param questionCode mã câu hỏi, vd "Q1"
 * @param optionCodes mã các đáp án đã chọn; để trống ở câu chọn thương hiệu
 * @param brandId thương hiệu đã chọn ở câu chọn thương hiệu; null = chưa có thương hiệu cố định
 */
public record SurveyAnswer(
    @NotBlank(message = "Thiếu mã câu hỏi") @Size(max = 10, message = "Mã câu hỏi không hợp lệ")
        String questionCode,
    @Size(max = 10, message = "Chọn quá nhiều đáp án")
        List<@NotBlank(message = "Mã đáp án không được để trống") String> optionCodes,
    Long brandId) {

  /** Mã các đáp án đã chọn, danh sách rỗng nếu không có. */
  public List<String> chosenCodes() {
    return optionCodes == null ? List.of() : optionCodes;
  }
}
