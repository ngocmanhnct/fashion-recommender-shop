package com.manhdn.fashionshop.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Bảng voucher_recipient: khách được tặng mã riêng (BR-43). Dùng JDBC vì một chiến dịch ghi một lúc
 * cả nghìn dòng và bảng không cần entity.
 */
@Repository
public class VoucherRecipientRepository {

  private final NamedParameterJdbcTemplate jdbc;

  /** Nhận NamedParameterJdbcTemplate qua constructor. */
  public VoucherRecipientRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Thêm các khách được tặng mã voucherId (ghi theo lô). */
  public void addAll(long voucherId, Collection<Long> accountIds) {
    addAll(voucherId, accountIds, false);
  }

  /**
   * Thêm các khách vào danh sách của mã voucherId (ghi theo lô).
   *
   * @param voucherId mã số voucher
   * @param accountIds các khách
   * @param heldOut true nếu là khách bị giữ lại làm nhóm đối chứng: có tên để đo, nhưng không dùng
   *     được và không thấy mã
   */
  public void addAll(long voucherId, Collection<Long> accountIds, boolean heldOut) {
    SqlParameterSource[] rows =
        accountIds.stream()
            .map(id ->
                new MapSqlParameterSource("voucherId", voucherId)
                    .addValue("accountId", id)
                    .addValue("heldOut", heldOut))
            .toArray(SqlParameterSource[]::new);
    jdbc.batchUpdate(
        "INSERT INTO voucher_recipient (voucher_id, account_id, held_out)"
            + " VALUES (:voucherId, :accountId, :heldOut)",
        rows);
  }

  /**
   * Hành vi mua của khách nhận mã và khách bị giữ lại của các mã, để so với nhau: số khách, số
   * khách đặt ít nhất 1 đơn chưa hủy (dùng mã hay không) và tổng tiền hàng trước giảm của các đơn
   * đó, trong khoảng từ lúc nhận (lần tặng tay: lúc tạo chiến dịch) đến hạn riêng (không có thì hạn
   * của mã). Thời điểm nhận, hạn riêng, hạn của mã lưu giờ Việt Nam, còn orders.created_at lưu giờ
   * JVM: vnAheadSeconds là số giây giờ Việt Nam đi trước giờ JVM, để đổi về cùng giờ.
   *
   * @param voucherIds các mã
   * @param vnAheadSeconds giờ Việt Nam trừ giờ JVM, tính bằng giây
   * @return mỗi mã tối đa 2 dòng (nhận mã, bị giữ lại)
   */
  public List<GroupBehavior> behaviors(Collection<Long> voucherIds, long vnAheadSeconds) {
    String window =
        " FROM orders o WHERE o.account_id = r.account_id AND o.status <> 'CANCELLED'"
            + " AND o.created_at >= COALESCE(DATE_SUB(r.granted_at, INTERVAL :shift SECOND),"
            + " c.created_at)"
            + " AND o.created_at <= DATE_SUB(COALESCE(r.expires_at, v.valid_until),"
            + " INTERVAL :shift SECOND)";
    return jdbc.query(
        "SELECT r.voucher_id, r.held_out, COUNT(*) AS customers,"
            + " COALESCE(SUM(EXISTS (SELECT 1"
            + window
            + ")), 0) AS buyers,"
            + " COALESCE(SUM((SELECT SUM(o.subtotal_amount)"
            + window
            + ")), 0) AS goods"
            + " FROM voucher_recipient r"
            + " JOIN segment_campaign c ON c.voucher_id = r.voucher_id"
            + " JOIN voucher v ON v.id = r.voucher_id"
            + " WHERE r.voucher_id IN (:voucherIds)"
            + " GROUP BY r.voucher_id, r.held_out",
        new MapSqlParameterSource("voucherIds", voucherIds).addValue("shift", vnAheadSeconds),
        (rs, row) ->
            new GroupBehavior(
                rs.getLong("voucher_id"),
                rs.getBoolean("held_out"),
                rs.getLong("customers"),
                rs.getLong("buyers"),
                rs.getLong("goods")));
  }

  /**
   * Lần tặng mã voucherId cho khách accountId; rỗng nếu khách không được tặng.
   *
   * @param voucherId mã số voucher
   * @param accountId tài khoản khách
   * @return hạn dùng riêng của khách (null nếu theo hạn của mã)
   */
  public Optional<Grant> find(long voucherId, long accountId) {
    return jdbc
        .query(
            "SELECT voucher_id, expires_at FROM voucher_recipient"
                + " WHERE voucher_id = :voucherId AND account_id = :accountId AND NOT held_out",
            Map.of("voucherId", voucherId, "accountId", accountId),
            VoucherRecipientRepository::grant)
        .stream()
        .findFirst();
  }

  /** Các mã riêng đã tặng cho khách accountId, kèm hạn dùng riêng. */
  public List<Grant> grantsOf(long accountId) {
    return jdbc.query(
        "SELECT voucher_id, expires_at FROM voucher_recipient"
            + " WHERE account_id = :accountId AND NOT held_out",
        Map.of("accountId", accountId),
        VoucherRecipientRepository::grant);
  }

  private static Grant grant(ResultSet rs, int row) throws SQLException {
    Timestamp expires = rs.getTimestamp("expires_at");
    return new Grant(rs.getLong("voucher_id"), expires == null ? null : expires.toLocalDateTime());
  }

  /**
   * Một mã khách được tặng.
   *
   * @param voucherId mã số voucher
   * @param expiresAt hạn dùng riêng của khách (giờ Việt Nam), null nếu theo hạn của mã
   */
  public record Grant(long voucherId, LocalDateTime expiresAt) {}

  /**
   * Hành vi mua của một nhóm khách của một mã.
   *
   * @param voucherId mã số voucher
   * @param heldOut true là khách bị giữ lại (đối chứng), false là khách nhận mã
   * @param customers số khách
   * @param buyers số khách đặt ít nhất 1 đơn chưa hủy trong khoảng đo
   * @param goods tổng tiền hàng trước giảm của các đơn đó (VNĐ)
   */
  public record GroupBehavior(
      long voucherId, boolean heldOut, long customers, long buyers, long goods) {}
}
