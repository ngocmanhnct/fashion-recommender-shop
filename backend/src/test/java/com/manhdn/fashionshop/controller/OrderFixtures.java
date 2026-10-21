package com.manhdn.fashionshop.controller;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Thêm đơn hàng, dòng đơn và đánh giá bằng SQL cho test, vì chưa có entity và API đặt hàng, đánh
 * giá. Dùng được trong transaction của test (cùng kết nối) lẫn ngoài transaction.
 */
final class OrderFixtures {

  private OrderFixtures() {}

  /** Thêm đơn hàng COD với trạng thái cho trước; trả về mã đơn. */
  static long insertOrder(JdbcTemplate jdbc, long accountId, String status) {
    return insert(
        jdbc,
        """
        INSERT INTO orders (account_id, status, payment_method, idempotency_key,
            recipient_name_snapshot, phone_snapshot, address_detail_snapshot,
            subtotal_amount, total_amount, created_at, updated_at)
        VALUES (?, ?, 'COD', ?, 'Người nhận thử', '0900000000', 'Địa chỉ thử', 0, 0, NOW(), NOW())
        """,
        accountId,
        status,
        UUID.randomUUID().toString());
  }

  /** Thêm dòng đơn cho biến thể với số lượng cho trước; trả về mã dòng đơn. */
  static long insertOrderItem(JdbcTemplate jdbc, long orderId, long variantId, int quantity) {
    return insert(
        jdbc,
        """
        INSERT INTO order_item (order_id, variant_id, product_name_snapshot,
            unit_price_snapshot, quantity, line_total)
        VALUES (?, ?, 'Sản phẩm thử', 1000, ?, ?)
        """,
        orderId,
        variantId,
        quantity,
        1000L * quantity);
  }

  /** Thêm đánh giá của tài khoản cho sản phẩm, gắn với một dòng đơn có sẵn. */
  static void insertReview(
      JdbcTemplate jdbc, long accountId, long productId, long orderItemId, int rating) {
    jdbc.update(
        """
        INSERT INTO review (account_id, product_id, order_item_id, rating, created_at, updated_at)
        VALUES (?, ?, ?, ?, NOW(), NOW())
        """,
        accountId,
        productId,
        orderItemId,
        rating);
  }

  private static long insert(JdbcTemplate jdbc, String sql, Object... args) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
          }
          return ps;
        },
        keys);
    return keys.getKeyAs(Number.class).longValue();
  }
}
