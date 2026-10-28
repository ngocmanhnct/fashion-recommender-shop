package com.manhdn.fashionshop.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Tỷ lệ bấm vào gợi ý (CTR) trong một khoảng ngày: tổng và theo từng vị trí, nguồn.
 *
 * @param from ngày đầu (tính cả ngày này)
 * @param to ngày cuối (tính cả ngày này)
 * @param total tổng mọi vị trí, nguồn
 * @param rows theo vị trí rồi nguồn, xếp theo tên
 */
public record RecommendationCtrResponse(LocalDate from, LocalDate to, Row total, List<Row> rows) {

  /**
   * Số lượt hiện, lượt bấm và tỷ lệ bấm của một nhóm.
   *
   * @param placement vị trí khối gợi ý; null ở dòng tổng
   * @param source nguồn gợi ý; null ở dòng tổng
   * @param shown số lượt hiện
   * @param clicked số lượt bấm
   * @param ctr lượt bấm ÷ lượt hiện × 100, làm tròn 2 chữ số; null khi chưa có lượt hiện
   */
  public record Row(String placement, String source, long shown, long clicked, Double ctr) {

    /** Tạo dòng và tính tỷ lệ bấm. */
    public static Row of(String placement, String source, long shown, long clicked) {
      Double ctr = shown == 0 ? null : Math.round(clicked * 10000.0 / shown) / 100.0;
      return new Row(placement, source, shown, clicked, ctr);
    }
  }
}
