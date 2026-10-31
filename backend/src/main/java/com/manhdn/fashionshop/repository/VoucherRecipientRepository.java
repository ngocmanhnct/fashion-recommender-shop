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
 * Bảng voucher_recipient: khách được tặng mã riêng (BR-44). Dùng JDBC vì một chiến dịch ghi một lúc
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
    SqlParameterSource[] rows =
        accountIds.stream()
            .map(id -> new MapSqlParameterSource("voucherId", voucherId).addValue("accountId", id))
            .toArray(SqlParameterSource[]::new);
    jdbc.batchUpdate(
        "INSERT INTO voucher_recipient (voucher_id, account_id) VALUES (:voucherId, :accountId)",
        rows);
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
                + " WHERE voucher_id = :voucherId AND account_id = :accountId",
            Map.of("voucherId", voucherId, "accountId", accountId),
            VoucherRecipientRepository::grant)
        .stream()
        .findFirst();
  }

  /** Các mã riêng đã tặng cho khách accountId, kèm hạn dùng riêng. */
  public List<Grant> grantsOf(long accountId) {
    return jdbc.query(
        "SELECT voucher_id, expires_at FROM voucher_recipient WHERE account_id = :accountId",
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
}
