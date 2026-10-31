package com.manhdn.fashionshop.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Các truy vấn gộp cho trang tổng quan của Quản trị viên (US-28). Dùng JDBC vì đều là phép đếm,
 * cộng trên nhiều bảng. Khoảng thời gian [start, end) tính theo giờ lưu trong CSDL (giờ JVM).
 */
@Repository
public class DashboardRepository {

  private static final String IN_RANGE = " o.created_at >= :start AND o.created_at < :end";

  private final NamedParameterJdbcTemplate jdbc;

  /** Nhận NamedParameterJdbcTemplate qua constructor. */
  public DashboardRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Số đơn và tiền theo ngày. Ngày của đơn là ngày của {@code created_at + shiftSeconds} (đổi giờ
   * lưu trong CSDL sang giờ Việt Nam).
   */
  public List<DayRow> daily(LocalDateTime start, LocalDateTime end, long shiftSeconds) {
    return jdbc.query(
        "SELECT DATE(o.created_at + INTERVAL :shift SECOND) AS day, COUNT(*) AS orders,"
            + " SUM(o.status = 'DELIVERED') AS delivered,"
            + " COALESCE(SUM(IF(o.status = 'DELIVERED', o.total_amount, 0)), 0) AS revenue,"
            + " COALESCE(SUM(IF(o.status = 'DELIVERED', o.subtotal_amount, 0)), 0) AS goods"
            + " FROM orders o WHERE"
            + IN_RANGE
            + " GROUP BY day ORDER BY day",
        Map.of("start", start, "end", end, "shift", shiftSeconds),
        (rs, i) ->
            new DayRow(
                rs.getObject("day", LocalDate.class),
                rs.getLong("orders"),
                rs.getLong("delivered"),
                rs.getLong("revenue"),
                rs.getLong("goods")));
  }

  /**
   * Số đơn đặt trong khoảng theo trạng thái (trạng thái không có đơn thì không có trong kết quả).
   */
  public Map<String, Long> ordersByStatus(LocalDateTime start, LocalDateTime end) {
    Map<String, Long> result = new HashMap<>();
    jdbc.query(
        "SELECT o.status, COUNT(*) AS n FROM orders o WHERE" + IN_RANGE + " GROUP BY o.status",
        Map.of("start", start, "end", end),
        rs -> {
          result.put(rs.getString("status"), rs.getLong("n"));
        });
    return result;
  }

  /** Sản phẩm bán nhiều nhất (theo số lượng) ở đơn Đã giao đặt trong khoảng. */
  public List<TopProductRow> topProducts(LocalDateTime start, LocalDateTime end, int limit) {
    return jdbc.query(
        "SELECT p.id, p.name, SUM(oi.quantity) AS quantity, SUM(oi.line_total) AS revenue"
            + " FROM order_item oi JOIN orders o ON o.id = oi.order_id"
            + " JOIN product_variant v ON v.id = oi.variant_id"
            + " JOIN product p ON p.id = v.product_id"
            + " WHERE o.status = 'DELIVERED' AND"
            + IN_RANGE
            + " GROUP BY p.id, p.name ORDER BY quantity DESC, revenue DESC, p.id LIMIT :limit",
        Map.of("start", start, "end", end, "limit", limit),
        (rs, i) ->
            new TopProductRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getLong("quantity"),
                rs.getLong("revenue")));
  }

  /** Số tài khoản khách hàng đăng ký trong khoảng. */
  public long newCustomers(LocalDateTime start, LocalDateTime end) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM account WHERE role = 'CUSTOMER'"
                + " AND created_at >= :start AND created_at < :end",
            Map.of("start", start, "end", end),
            Long.class);
    return n == null ? 0 : n;
  }

  /**
   * Tiền hàng từ gợi ý: tổng thành tiền các dòng của đơn Đã giao đặt trong khoảng mà khách đã bấm
   * sản phẩm đó ở một khối gợi ý trong {@code windowDays} ngày trước lúc đặt (BR-47).
   */
  public long recommendedRevenue(LocalDateTime start, LocalDateTime end, int windowDays) {
    Long sum =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(oi.line_total), 0) FROM order_item oi"
                + " JOIN orders o ON o.id = oi.order_id"
                + " JOIN product_variant v ON v.id = oi.variant_id"
                + " WHERE o.status = 'DELIVERED' AND"
                + IN_RANGE
                + " AND EXISTS (SELECT 1 FROM product_event_log e"
                + " WHERE e.account_id = o.account_id AND e.product_id = v.product_id"
                + " AND e.event_type = 'RECOMMENDATION_CLICKED'"
                + " AND e.created_at <= o.created_at"
                + " AND e.created_at >= o.created_at - INTERVAL :window DAY)",
            Map.of("start", start, "end", end, "window", windowDays),
            Long.class);
    return sum == null ? 0 : sum;
  }

  /**
   * Số liệu một ngày.
   *
   * @param day ngày (giờ Việt Nam)
   * @param orders số đơn đặt, mọi trạng thái
   * @param delivered số đơn Đã giao
   * @param revenue tổng tiền khách trả của đơn Đã giao
   * @param goods tiền hàng của đơn Đã giao
   */
  public record DayRow(LocalDate day, long orders, long delivered, long revenue, long goods) {}

  /**
   * Một sản phẩm bán chạy.
   *
   * @param productId mã sản phẩm
   * @param name tên sản phẩm
   * @param quantity số lượng đã bán
   * @param revenue tiền hàng
   */
  public record TopProductRow(long productId, String name, long quantity, long revenue) {}
}
