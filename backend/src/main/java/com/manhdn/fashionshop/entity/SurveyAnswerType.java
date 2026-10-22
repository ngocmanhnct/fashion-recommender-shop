package com.manhdn.fashionshop.entity;

/** Cách trả lời một câu hỏi khảo sát lần đầu (US-23). */
public enum SurveyAnswerType {
  /** Chọn đúng 1 đáp án. */
  SINGLE,
  /** Chọn từ 1 đến số đáp án tối đa của câu hỏi. */
  MULTIPLE,
  /** Chọn 1 thương hiệu thuộc các danh mục đã chọn, hoặc không chọn thương hiệu nào. */
  BRAND
}
