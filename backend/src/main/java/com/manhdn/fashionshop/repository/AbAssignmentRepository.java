package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.AbVariant;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Nhóm của khách trong thử nghiệm A/B (bảng ab_assignment) và số liệu của từng nhóm (US-27). Dùng
 * JDBC: số liệu là các truy vấn gộp trên nhật ký sự kiện và đơn hàng.
 */
@Repository
public class AbAssignmentRepository {

  /** Các khối gợi ý ở trang chủ được tính vào thử nghiệm. */
  public static final List<String> HOME_PLACEMENTS = List.of("HOME", "HOME_RECENT");

  private static final String WINDOW =
      " e.account_id = a.account_id AND e.created_at >= a.assigned_at AND e.created_at < :end"
          + " AND e.placement IN (:placements)";

  private final NamedParameterJdbcTemplate jdbc;

  /** Nhận NamedParameterJdbcTemplate qua constructor. */
  public AbAssignmentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Ghi nhóm của khách nếu chưa có; khách đã có nhóm giữ nguyên nhóm và lúc chia cũ.
   *
   * @return nhóm đã lưu của khách
   */
  public AbVariant assign(long experimentId, long accountId, AbVariant variant, LocalDateTime now) {
    jdbc.update(
        "INSERT INTO ab_assignment (experiment_id, account_id, variant, assigned_at)"
            + " VALUES (:experiment, :account, :variant, :now)"
            + " ON DUPLICATE KEY UPDATE account_id = account_id",
        Map.of(
            "experiment", experimentId,
            "account", accountId,
            "variant", variant.name(),
            "now", now));
    return find(experimentId, accountId).orElse(variant);
  }

  /** Nhóm của khách trong thử nghiệm, nếu khách đã được chia. */
  public Optional<AbVariant> find(long experimentId, long accountId) {
    return jdbc
        .queryForList(
            "SELECT variant FROM ab_assignment"
                + " WHERE experiment_id = :experiment AND account_id = :account",
            Map.of("experiment", experimentId, "account", accountId),
            String.class)
        .stream()
        .findFirst()
        .map(AbVariant::valueOf);
  }

  /**
   * Số liệu của từng nhóm, chỉ tính hành động của khách từ lúc được chia nhóm đến {@code end}.
   *
   * @param experimentId mã thử nghiệm
   * @param end lúc dừng thử nghiệm, hoặc bây giờ nếu còn chạy
   * @return số liệu theo nhóm; nhóm chưa có khách có mọi số bằng 0
   */
  public Map<AbVariant, VariantStats> stats(long experimentId, LocalDateTime end) {
    Map<String, Object> params =
        Map.of("experiment", experimentId, "end", end, "placements", HOME_PLACEMENTS);
    Map<AbVariant, long[]> values = new EnumMap<>(AbVariant.class);
    for (AbVariant v : AbVariant.values()) {
      values.put(v, new long[8]);
    }
    jdbc.query(
        "SELECT variant, COUNT(*) AS n FROM ab_assignment WHERE experiment_id = :experiment"
            + " GROUP BY variant",
        params,
        rs -> {
          values.get(AbVariant.valueOf(rs.getString("variant")))[0] = rs.getLong("n");
        });
    jdbc.query(
        "SELECT a.variant,"
            + " SUM(e.event_type = 'RECOMMENDATION_SHOWN') AS shown,"
            + " SUM(e.event_type = 'RECOMMENDATION_CLICKED') AS clicks,"
            + " COUNT(DISTINCT IF(e.event_type = 'RECOMMENDATION_CLICKED', e.account_id, NULL))"
            + " AS users_clicked"
            + " FROM ab_assignment a JOIN product_event_log e ON"
            + WINDOW
            + " WHERE a.experiment_id = :experiment"
            + " AND e.event_type IN ('RECOMMENDATION_SHOWN', 'RECOMMENDATION_CLICKED')"
            + " GROUP BY a.variant",
        params,
        rs -> {
          long[] v = values.get(AbVariant.valueOf(rs.getString("variant")));
          v[1] = rs.getLong("shown");
          v[2] = rs.getLong("clicks");
          v[3] = rs.getLong("users_clicked");
        });
    jdbc.query(
        "SELECT a.variant, COUNT(DISTINCT o.account_id) AS users_ordered, COUNT(*) AS orders,"
            + " COALESCE(SUM(o.total_amount), 0) AS revenue"
            + " FROM ab_assignment a JOIN orders o ON o.account_id = a.account_id"
            + " AND o.created_at >= a.assigned_at AND o.created_at < :end"
            + " AND o.status <> 'CANCELLED'"
            + " WHERE a.experiment_id = :experiment GROUP BY a.variant",
        params,
        rs -> {
          long[] v = values.get(AbVariant.valueOf(rs.getString("variant")));
          v[4] = rs.getLong("users_ordered");
          v[5] = rs.getLong("orders");
          v[6] = rs.getLong("revenue");
        });
    jdbc.query(
        "SELECT a.variant, COUNT(DISTINCT a.account_id) AS users_bought_clicked"
            + " FROM ab_assignment a JOIN orders o ON o.account_id = a.account_id"
            + " AND o.created_at >= a.assigned_at AND o.created_at < :end"
            + " AND o.status <> 'CANCELLED'"
            + " WHERE a.experiment_id = :experiment AND EXISTS ("
            + " SELECT 1 FROM order_item oi JOIN product_variant pv ON pv.id = oi.variant_id"
            + " JOIN product_event_log e ON e.product_id = pv.product_id AND"
            + WINDOW
            + " AND e.event_type = 'RECOMMENDATION_CLICKED' AND e.created_at <= o.created_at"
            + " WHERE oi.order_id = o.id)"
            + " GROUP BY a.variant",
        params,
        rs -> {
          values.get(AbVariant.valueOf(rs.getString("variant")))[7] =
              rs.getLong("users_bought_clicked");
        });
    Map<AbVariant, VariantStats> result = new EnumMap<>(AbVariant.class);
    values.forEach(
        (variant, v) ->
            result.put(variant, new VariantStats(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7])));
    return result;
  }

  /**
   * Số liệu thô của một nhóm.
   *
   * @param users số khách được chia vào nhóm
   * @param shown lượt hiện sản phẩm gợi ý ở trang chủ (mỗi sản phẩm một lượt)
   * @param clicks lượt bấm sản phẩm gợi ý ở trang chủ
   * @param usersClicked số khách bấm ít nhất 1 lần
   * @param usersOrdered số khách đặt ít nhất 1 đơn chưa hủy
   * @param orders số đơn chưa hủy
   * @param revenue tổng tiền các đơn đó (VNĐ, sau giảm giá, gồm phí vận chuyển)
   * @param usersBoughtClicked số khách đặt đơn có sản phẩm họ đã bấm từ gợi ý trang chủ trước đó
   */
  public record VariantStats(
      long users,
      long shown,
      long clicks,
      long usersClicked,
      long usersOrdered,
      long orders,
      long revenue,
      long usersBoughtClicked) {}
}
