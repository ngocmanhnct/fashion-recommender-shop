package com.manhdn.fashionshop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Các câu trả lời khảo sát theo đúng thứ tự đã đi qua cây câu hỏi, bắt đầu từ câu đầu tiên.
 *
 * @param answers các câu trả lời, tối đa 12
 */
public record SurveyAnswersRequest(
    @NotNull(message = "Danh sách câu trả lời không được để trống")
        @Size(max = 12, message = "Quá nhiều câu trả lời")
        List<@NotNull(message = "Câu trả lời không được để trống") @Valid SurveyAnswer> answers) {}
