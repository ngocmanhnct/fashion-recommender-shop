package com.manhdn.fashionshop.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

  /** Khách accountId có được tặng mã voucherId không. */
  public boolean contains(long voucherId, long accountId) {
    Integer found =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM voucher_recipient"
                + " WHERE voucher_id = :voucherId AND account_id = :accountId",
            Map.of("voucherId", voucherId, "accountId", accountId),
            Integer.class);
    return found != null && found > 0;
  }

  /** Mã các voucher riêng đã tặng cho khách accountId. */
  public List<Long> voucherIdsOf(long accountId) {
    return jdbc.queryForList(
        "SELECT voucher_id FROM voucher_recipient WHERE account_id = :accountId",
        Map.of("accountId", accountId),
        Long.class);
  }
}
