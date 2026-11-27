package com.manhdn.fashionshop.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Lúc khách vào nhóm hiện tại (bảng segment_entry) và việc tự tặng mã cho khách mới vào nhóm
 * (US-24). Dùng JDBC: mỗi lượt là vài câu INSERT … SELECT trên cả bảng khách.
 */
@Repository
public class SegmentEntryRepository {

  /** Mỗi khách nhận tối đa 1 mã tự tặng trong chừng này ngày, dù đổi nhóm nhiều lần (BR-43). */
  public static final int AUTO_OFFER_COOLDOWN_DAYS = 30;

  private final NamedParameterJdbcTemplate jdbc;

  /** Nhận NamedParameterJdbcTemplate qua constructor. */
  public SegmentEntryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Ghi nhóm hiện tại của mọi khách đã phân nhóm: khách mới phân nhóm hoặc vừa đổi nhóm có lúc vào
   * nhóm là now, khách vẫn ở nhóm cũ giữ nguyên lúc vào. Đổi nhóm khi mô hình đã khác lần ghi trước
   * mà F, M của khách không đổi (khách không mua thêm, chỉ vì huấn luyện lại tâm nhóm dịch chuyển)
   * thì đánh dấu moved_by_model, chương trình tự tặng bỏ qua khách đó. MySQL tính các phép gán của
   * ON DUPLICATE KEY UPDATE từ trái sang phải, nên moved_by_model và entered_at được gán trước
   * segment_code, còn mô hình, F, M gán sau cùng.
   *
   * @param now giờ JVM, cùng giờ với segment_campaign.created_at
   */
  public void sync(LocalDateTime now) {
    jdbc.update(
        "INSERT INTO segment_entry (account_id, segment_code, entered_at, model_id,"
            + " rfm_frequency, rfm_monetary, moved_by_model)"
            + " SELECT * FROM (SELECT a.account_id, cs.code, :now AS entered_at, cs.model_id,"
            + " a.rfm_frequency, a.rfm_monetary, FALSE AS moved"
            + " FROM account_segment a JOIN customer_segment cs ON cs.id = a.segment_id)"
            + " AS incoming"
            + " ON DUPLICATE KEY UPDATE"
            + " moved_by_model = IF(segment_entry.segment_code = incoming.code,"
            + " segment_entry.moved_by_model,"
            + " segment_entry.model_id IS NOT NULL AND segment_entry.model_id <> incoming.model_id"
            + " AND segment_entry.rfm_frequency = incoming.rfm_frequency"
            + " AND segment_entry.rfm_monetary = incoming.rfm_monetary),"
            + " entered_at = IF(segment_entry.segment_code = incoming.code,"
            + " segment_entry.entered_at, incoming.entered_at),"
            + " segment_code = incoming.code,"
            + " model_id = incoming.model_id,"
            + " rfm_frequency = incoming.rfm_frequency,"
            + " rfm_monetary = incoming.rfm_monetary",
        Map.of("now", now));
  }

  /**
   * Các chương trình tự tặng đang chạy có mã còn dùng được lúc nowVn, kèm số khách đã nhận (kể cả
   * khách bị giữ lại làm đối chứng).
   *
   * @param nowVn giờ Việt Nam, như thời hạn của mã
   * @return chương trình đang chạy, cũ nhất trước
   */
  public List<AutoCampaign> activeAutoCampaigns(LocalDateTime nowVn) {
    return jdbc.query(
        "SELECT c.voucher_id, c.segment_code, c.created_at, c.holdout_percent, c.max_recipients,"
            + " (SELECT COUNT(*) FROM voucher_recipient r WHERE r.voucher_id = c.voucher_id)"
            + " AS taken"
            + " FROM segment_campaign c JOIN voucher v ON v.id = c.voucher_id"
            + " WHERE c.auto_enabled AND v.is_active"
            + " AND v.valid_from <= :nowVn AND v.valid_until > :nowVn"
            + " ORDER BY c.id",
        Map.of("nowVn", nowVn),
        (rs, row) ->
            new AutoCampaign(
                rs.getLong("voucher_id"),
                rs.getString("segment_code"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getInt("holdout_percent"),
                rs.getObject("max_recipients", Integer.class),
                rs.getInt("taken")));
  }

  /**
   * Khách đủ điều kiện nhận mã của chương trình tự tặng: đang thuộc nhóm, vào nhóm sau lúc bật
   * chương trình (không tính đổi nhóm chỉ vì huấn luyện lại), chưa có trong danh sách của mã này,
   * và chưa nhận (hay bị giữ lại ở) chương trình tự tặng nào trong {@link
   * #AUTO_OFFER_COOLDOWN_DAYS} ngày qua (lượt phân nhóm lại có thể chuyển nhiều khách qua lại giữa
   * các nhóm; không có giới hạn này khách sẽ nhận mã của mỗi nhóm đi qua). Vào nhóm sớm trước.
   *
   * @param campaign chương trình
   * @param nowVn giờ Việt Nam
   * @param limit số khách tối đa
   * @return mã tài khoản
   */
  public List<Long> eligibleForAuto(AutoCampaign campaign, LocalDateTime nowVn, int limit) {
    return jdbc.queryForList(
        "SELECT e.account_id FROM segment_entry e"
            + " JOIN account_segment a ON a.account_id = e.account_id"
            + " WHERE e.segment_code = :segment AND e.entered_at > :startedAt"
            + " AND NOT e.moved_by_model"
            + " AND NOT EXISTS (SELECT 1 FROM voucher_recipient r"
            + " WHERE r.voucher_id = :voucherId AND r.account_id = e.account_id)"
            + " AND NOT EXISTS (SELECT 1 FROM voucher_recipient recent"
            + " JOIN segment_campaign auto ON auto.voucher_id = recent.voucher_id"
            + " WHERE auto.target = 'AUTO' AND recent.account_id = e.account_id"
            + " AND recent.granted_at > DATE_SUB(:nowVn, INTERVAL :cooldown DAY))"
            + " ORDER BY e.entered_at, e.account_id LIMIT :limit",
        new MapSqlParameterSource("segment", campaign.segmentCode())
            .addValue("startedAt", campaign.createdAt())
            .addValue("voucherId", campaign.voucherId())
            .addValue("nowVn", nowVn)
            .addValue("cooldown", AUTO_OFFER_COOLDOWN_DAYS)
            .addValue("limit", limit),
        Long.class);
  }

  /**
   * Ghi khách vào danh sách của chương trình tự tặng: hạn riêng là validDays ngày kể từ nowVn,
   * không quá hạn của mã; khách bị giữ lại làm đối chứng cũng có lúc nhận và hạn như vậy (để đo
   * cùng khoảng thời gian) nhưng không dùng được mã.
   *
   * @param voucherId mã số voucher của chương trình
   * @param accountIds các khách
   * @param heldOut true nếu là khách bị giữ lại
   * @param nowVn giờ Việt Nam
   */
  public void addAutoRecipients(
      long voucherId, Collection<Long> accountIds, boolean heldOut, LocalDateTime nowVn) {
    if (accountIds.isEmpty()) {
      return;
    }
    SqlParameterSource[] rows =
        accountIds.stream()
            .map(id ->
                new MapSqlParameterSource("voucherId", voucherId)
                    .addValue("accountId", id)
                    .addValue("heldOut", heldOut)
                    .addValue("nowVn", nowVn))
            .toArray(SqlParameterSource[]::new);
    jdbc.batchUpdate(
        "INSERT INTO voucher_recipient (voucher_id, account_id, granted_at, expires_at, held_out)"
            + " SELECT :voucherId, :accountId, :nowVn,"
            + " LEAST(DATE_ADD(:nowVn, INTERVAL c.valid_days DAY), v.valid_until), :heldOut"
            + " FROM segment_campaign c JOIN voucher v ON v.id = c.voucher_id"
            + " WHERE c.voucher_id = :voucherId",
        rows);
  }

  /** Cập nhật số khách đã nhận mã và số khách bị giữ lại của các chương trình tự tặng. */
  public void refreshAutoRecipientCounts() {
    jdbc.update(
        "UPDATE segment_campaign c SET"
            + " recipient_count = (SELECT COUNT(*) FROM voucher_recipient r"
            + " WHERE r.voucher_id = c.voucher_id AND NOT r.held_out),"
            + " holdout_count = (SELECT COUNT(*) FROM voucher_recipient r"
            + " WHERE r.voucher_id = c.voucher_id AND r.held_out)"
            + " WHERE c.target = 'AUTO'",
        Map.of());
  }

  /**
   * Một chương trình tự tặng đang chạy.
   *
   * @param voucherId mã số voucher của chương trình
   * @param segmentCode mã nhóm
   * @param createdAt lúc bật (giờ JVM)
   * @param holdoutPercent phần trăm khách giữ lại làm đối chứng
   * @param maxRecipients trần số khách, null là không giới hạn
   * @param taken số khách đã có trong danh sách (kể cả khách bị giữ lại)
   */
  public record AutoCampaign(
      long voucherId,
      String segmentCode,
      LocalDateTime createdAt,
      int holdoutPercent,
      Integer maxRecipients,
      int taken) {}
}
