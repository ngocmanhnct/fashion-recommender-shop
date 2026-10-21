package com.manhdn.fashionshop.entity;

/**
 * Loại việc của một yêu cầu tính lại, cột recompute_request.task_type (Mục 3.15). Thứ tự khai báo
 * khớp thứ tự giá trị ENUM trong migration V3.
 */
public enum RecomputeTask {
  /** Tính lại danh sách gợi ý cá nhân hóa trên trang chủ (BR-20). */
  RECOMMENDATION,
  /** Gán tài khoản vào nhóm khách hàng gần nhất khi có đơn Đã giao (BR-23). */
  SEGMENT_ASSIGN
}
