package com.manhdn.fashionshop.entity;

/** Cách đáp án của một câu hỏi khảo sát đổi danh mục quan tâm (migration V11). */
public enum SurveyCategoryMode {
  /** Mỗi đáp án thêm 1 nhóm danh mục (vd loại đồ cần tìm). */
  ADD,
  /**
   * Thu hẹp các nhóm đã có: mỗi nhóm chỉ giữ danh mục thuộc các đáp án đã chọn, nhóm không còn danh
   * mục nào thì giữ nguyên; chưa có nhóm nào thì thêm như {@link #ADD} (vd dịp sử dụng).
   */
  NARROW
}
