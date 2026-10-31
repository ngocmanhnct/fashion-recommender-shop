package com.manhdn.fashionshop.repository;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lúc khách vào nhóm hiện tại (bảng segment_entry) và việc tự tặng mã cho khách mới vào nhóm
 * (US-24). Dùng JDBC: mỗi lượt là vài câu INSERT … SELECT trên cả bảng khách.
 */
@Repository
public class SegmentEntryRepository {

  private final NamedParameterJdbcTemplate jdbc;

  /** Nhận NamedParameterJdbcTemplate qua constructor. */
  public SegmentEntryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Ghi nhóm hiện tại của mọi khách đã phân nhóm: khách mới phân nhóm hoặc vừa đổi nhóm có lúc vào
   * nhóm là now, khách vẫn ở nhóm cũ giữ nguyên lúc vào. entered_at được gán trước segment_code vì
   * MySQL tính các phép gán của ON DUPLICATE KEY UPDATE từ trái sang phải.
   *
   * @param now giờ JVM, cùng giờ với segment_campaign.created_at
   */
  public void sync(LocalDateTime now) {
    jdbc.update(
        "INSERT INTO segment_entry (account_id, segment_code, entered_at)"
            + " SELECT * FROM (SELECT a.account_id, cs.code, :now AS entered_at"
            + " FROM account_segment a JOIN customer_segment cs ON cs.id = a.segment_id)"
            + " AS incoming"
            + " ON DUPLICATE KEY UPDATE"
            + " entered_at = IF(segment_entry.segment_code = incoming.code,"
            + " segment_entry.entered_at, incoming.entered_at),"
            + " segment_code = incoming.code",
        Map.of("now", now));
  }

  /**
   * Thêm vào danh sách nhận mã của mỗi chương trình tự tặng đang chạy các khách đang thuộc nhóm và
   * vào nhóm sau lúc bật chương trình, chưa nhận mã đó. Hạn riêng của khách là validDays ngày kể từ
   * bây giờ, không quá hạn của mã. Chương trình có mã đã ngừng, chưa tới ngày hoặc hết hạn thì bỏ
   * qua.
   *
   * @param nowVn giờ Việt Nam, như thời hạn của mã
   * @return số khách vừa được tặng
   */
  public int grantAutoOffers(LocalDateTime nowVn) {
    return jdbc.update(
        "INSERT INTO voucher_recipient (voucher_id, account_id, granted_at, expires_at)"
            + " SELECT c.voucher_id, e.account_id, :nowVn,"
            + " LEAST(DATE_ADD(:nowVn, INTERVAL c.valid_days DAY), v.valid_until)"
            + " FROM segment_campaign c"
            + " JOIN voucher v ON v.id = c.voucher_id"
            + " JOIN segment_entry e ON e.segment_code = c.segment_code"
            + " AND e.entered_at > c.created_at"
            + " JOIN account_segment a ON a.account_id = e.account_id"
            + " WHERE c.auto_enabled AND v.is_active"
            + " AND v.valid_from <= :nowVn AND v.valid_until > :nowVn"
            + " AND NOT EXISTS (SELECT 1 FROM voucher_recipient r"
            + " WHERE r.voucher_id = c.voucher_id AND r.account_id = e.account_id)",
        Map.of("nowVn", nowVn));
  }

  /** Cập nhật số khách đã nhận mã của các chương trình tự tặng. */
  public void refreshAutoRecipientCounts() {
    jdbc.update(
        "UPDATE segment_campaign c SET recipient_count ="
            + " (SELECT COUNT(*) FROM voucher_recipient r WHERE r.voucher_id = c.voucher_id)"
            + " WHERE c.target = 'AUTO'",
        Map.of());
  }
}
