package com.manhdn.fashionshop.entity;

/** Trạng thái khảo sát lần đầu của một tài khoản (BR-37). */
public enum SurveyStatus {
  /** Đã làm hết khảo sát (nhánh mua cho bản thân). */
  COMPLETED,
  /** Đã bỏ qua: khảo sát không tự hiện lại, khách vẫn làm được khi muốn. */
  SKIPPED
}
