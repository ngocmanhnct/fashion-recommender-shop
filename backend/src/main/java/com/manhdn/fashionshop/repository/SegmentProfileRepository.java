package com.manhdn.fashionshop.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Các phép thống kê cho hồ sơ nhóm khách hàng (US-25), viết bằng SQL vì gộp nhiều bảng (đơn, dòng
 * đơn, sản phẩm, danh mục) theo nhóm hiện tại của khách. Chỉ tính đơn Đã giao, như R, F, M (BR-23).
 */
@Repository
public class SegmentProfileRepository {

  private final NamedParameterJdbcTemplate jdbc;

  /** Nhận NamedParameterJdbcTemplate qua constructor. */
  public SegmentProfileRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Lịch sử mua của 1 khách trong nhóm: số đơn Đã giao, đơn đầu và đơn gần nhất. */
  public record CustomerPurchases(
      long accountId,
      String fullName,
      String email,
      int orders,
      long amount,
      LocalDateTime firstOrderAt,
      LocalDateTime lastOrderAt) {}

  /** Số lượng sản phẩm đã mua của 1 danh mục. */
  public record CategoryQuantity(String name, long quantity) {}

  /** Doanh thu (tổng tiền đơn Đã giao) theo mã nhóm, tính theo nhóm hiện tại của khách. */
  public Map<Long, Long> revenueBySegment() {
    Map<Long, Long> result = new HashMap<>();
    jdbc.query(
        """
        SELECT s.segment_id, SUM(o.total_amount) AS revenue
        FROM account_segment s
        JOIN orders o ON o.account_id = s.account_id AND o.status = 'DELIVERED'
        GROUP BY s.segment_id
        """,
        rs -> {
          result.put(rs.getLong("segment_id"), rs.getLong("revenue"));
        });
    return result;
  }

  /** Lịch sử mua của từng khách trong nhóm (khách có ít nhất 1 đơn Đã giao). */
  public List<CustomerPurchases> purchases(long segmentId) {
    return jdbc.query(
        """
        SELECT a.id, a.full_name, a.email, COUNT(o.id) AS orders, SUM(o.total_amount) AS amount,
               MIN(o.created_at) AS first_order_at, MAX(o.created_at) AS last_order_at
        FROM account_segment s
        JOIN account a ON a.id = s.account_id
        JOIN orders o ON o.account_id = s.account_id AND o.status = 'DELIVERED'
        WHERE s.segment_id = :segmentId
        GROUP BY a.id, a.full_name, a.email
        """,
        new MapSqlParameterSource("segmentId", segmentId),
        (rs, i) ->
            new CustomerPurchases(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getInt("orders"),
                rs.getLong("amount"),
                rs.getTimestamp("first_order_at").toLocalDateTime(),
                rs.getTimestamp("last_order_at").toLocalDateTime()));
  }

  /** Các danh mục khách trong nhóm mua nhiều nhất (theo số lượng), nhiều trước. */
  public List<CategoryQuantity> topCategories(long segmentId, int limit) {
    return jdbc.query(
        """
        SELECT c.name, SUM(oi.quantity) AS quantity
        FROM account_segment s
        JOIN orders o ON o.account_id = s.account_id AND o.status = 'DELIVERED'
        JOIN order_item oi ON oi.order_id = o.id
        JOIN product_variant v ON v.id = oi.variant_id
        JOIN product p ON p.id = v.product_id
        JOIN category c ON c.id = p.category_id
        WHERE s.segment_id = :segmentId
        GROUP BY c.id, c.name
        ORDER BY quantity DESC, c.name
        LIMIT :limit
        """,
        new MapSqlParameterSource("segmentId", segmentId).addValue("limit", limit),
        (rs, i) -> new CategoryQuantity(rs.getString("name"), rs.getLong("quantity")));
  }

  /** Tổng số lượng sản phẩm khách trong nhóm đã mua (mẫu số của tỷ lệ từng danh mục). */
  public long totalQuantity(long segmentId) {
    Long total =
        jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(oi.quantity), 0)
            FROM account_segment s
            JOIN orders o ON o.account_id = s.account_id AND o.status = 'DELIVERED'
            JOIN order_item oi ON oi.order_id = o.id
            WHERE s.segment_id = :segmentId
            """,
            new MapSqlParameterSource("segmentId", segmentId),
            Long.class);
    return total == null ? 0 : total;
  }

  /** Số khách trong nhóm theo giới tính (MALE, FEMALE, OTHER; UNKNOWN là chưa khai). */
  public Map<String, Long> genderCounts(long segmentId) {
    return counts(
        """
        SELECT COALESCE(a.gender, 'UNKNOWN') AS bucket, COUNT(*) AS customers
        FROM account_segment s JOIN account a ON a.id = s.account_id
        WHERE s.segment_id = :segmentId
        GROUP BY bucket
        """,
        new MapSqlParameterSource("segmentId", segmentId));
  }

  /**
   * Số khách trong nhóm theo nhóm tuổi tại ngày today: UNDER_18, 18_24, 25_34, 35_44, 45_54,
   * 55_PLUS; UNKNOWN là chưa khai ngày sinh.
   */
  public Map<String, Long> ageCounts(long segmentId, LocalDate today) {
    return counts(
        """
        SELECT CASE
                 WHEN a.birthdate IS NULL THEN 'UNKNOWN'
                 WHEN TIMESTAMPDIFF(YEAR, a.birthdate, :today) < 18 THEN 'UNDER_18'
                 WHEN TIMESTAMPDIFF(YEAR, a.birthdate, :today) < 25 THEN '18_24'
                 WHEN TIMESTAMPDIFF(YEAR, a.birthdate, :today) < 35 THEN '25_34'
                 WHEN TIMESTAMPDIFF(YEAR, a.birthdate, :today) < 45 THEN '35_44'
                 WHEN TIMESTAMPDIFF(YEAR, a.birthdate, :today) < 55 THEN '45_54'
                 ELSE '55_PLUS'
               END AS bucket,
               COUNT(*) AS customers
        FROM account_segment s JOIN account a ON a.id = s.account_id
        WHERE s.segment_id = :segmentId
        GROUP BY bucket
        """,
        new MapSqlParameterSource("segmentId", segmentId).addValue("today", today));
  }

  private Map<String, Long> counts(String sql, MapSqlParameterSource params) {
    Map<String, Long> result = new HashMap<>();
    jdbc.query(
        sql,
        params,
        rs -> {
          result.put(rs.getString("bucket"), rs.getLong("customers"));
        });
    return result;
  }
}
