package com.manhdn.fashionshop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.RecomputeTask;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.ProductService;
import com.manhdn.fashionshop.service.RecomputeRequestService;
import com.manhdn.fashionshop.service.TransactionRetry;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kiểm tra ghi lượt xem (US-03, BR-19) và yêu cầu tính lại gợi ý (BR-20) với MySQL thật.
 *
 * <p>Lớp này cố ý không dùng {@code @Transactional}: yêu cầu tính lại chỉ được ghi ngay trước khi
 * giao dịch commit, mà transaction của test luôn rollback nên sẽ không bao giờ thấy. Mọi dòng test
 * tạo ra được xóa ở {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductViewIntegrationTest {

  private static final long FIVE_MINUTES_MICROS = 300_000_000L;
  private static final long THREE_SECONDS_MICROS = 3_000_000L;

  /** Còn hơn 4 phút nữa mới đến hạn: chắc chắn là thời gian chờ 5 phút của lượt xem. */
  private static final long VIEW_DELAY_LOWER_BOUND = 240_000_000L;

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private ProductService productService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private RecomputeRequestService recomputeRequestService;
  @Autowired private TransactionRetry transactionRetry;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;

  private final List<Long> accountIds = new ArrayList<>();
  private final List<Long> productIds = new ArrayList<>();
  private final List<Long> orderIds = new ArrayList<>();
  private long categoryId;

  @BeforeEach
  void createCategory() {
    categoryId = categoryService.create("Danh mục lượt xem " + UUID.randomUUID()).id();
  }

  @AfterEach
  void cleanUp() {
    deleteWhere("DELETE FROM recompute_request WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM product_view_log WHERE product_id IN (:ids)", productIds);
    deleteWhere("DELETE FROM product_view_log WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM order_item WHERE order_id IN (:ids)", orderIds);
    deleteWhere("DELETE FROM orders WHERE id IN (:ids)", orderIds);
    deleteWhere("DELETE FROM product_variant WHERE product_id IN (:ids)", productIds);
    deleteWhere("DELETE FROM product WHERE id IN (:ids)", productIds);
    jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
    deleteWhere("DELETE FROM account WHERE id IN (:ids)", accountIds);
  }

  // ---------- Ghi lượt xem qua API ----------

  @Test
  void viewIsLoggedAndRequestsRecomputeInFiveMinutesUtc() throws Exception {
    long account = account();
    long product = product();

    view(account, product).andExpect(status().isNoContent());

    assertThat(viewCount(account)).isEqualTo(1);
    Map<String, Object> request = request(account);
    assertThat(request.get("task_type")).isEqualTo("RECOMMENDATION");
    assertThat(((Number) request.get("version")).intValue()).isEqualTo(1);
    assertThat(((Number) request.get("attempts")).intValue()).isZero();
    assertThat(microsUntilDue(account)).isBetween(VIEW_DELAY_LOWER_BOUND, FIVE_MINUTES_MICROS);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP(3)) FROM recompute_request"
                    + " WHERE account_id = ?",
                Long.class,
                account))
        .isBetween(0L, 60L);
  }

  @Test
  void fifthDistinctProductInThirtyDaysIsAStrongAction() throws Exception {
    long account = account();
    List<Long> products = products(6);
    for (int i = 0; i < 4; i++) {
      view(account, products.get(i)).andExpect(status().isNoContent());
    }
    assertThat(microsUntilDue(account)).isGreaterThan(VIEW_DELAY_LOWER_BOUND);

    view(account, products.get(4)).andExpect(status().isNoContent());

    assertThat(microsUntilDue(account)).isBetween(-30_000_000L, THREE_SECONDS_MICROS);
    assertThat(version(account)).isEqualTo(5);

    // Lượt xem sau gộp vào yêu cầu đang chờ: giữ thời điểm đến hạn sớm hơn, tăng version
    Object dueAt = request(account).get("due_at");
    view(account, products.get(5)).andExpect(status().isNoContent());
    assertThat(request(account).get("due_at")).isEqualTo(dueAt);
    assertThat(version(account)).isEqualTo(6);

    // Chỉ lượt xem vừa đạt ngưỡng được tính lại sau 3 giây: sau khi ml-service đã xử lý yêu cầu,
    // sản phẩm mới tiếp theo trở lại thời gian chờ 5 phút
    jdbcTemplate.update("DELETE FROM recompute_request WHERE account_id = ?", account);
    view(account, product()).andExpect(status().isNoContent());
    assertThat(microsUntilDue(account)).isGreaterThan(VIEW_DELAY_LOWER_BOUND);
  }

  @Test
  void repeatViewNeverCountsAsReachingTheThreshold() throws Exception {
    long account = account();
    List<Long> products = products(5);
    for (int i = 0; i < 4; i++) {
      view(account, products.get(i)).andExpect(status().isNoContent());
    }

    view(account, products.get(3)).andExpect(status().isNoContent());

    assertThat(viewCount(account)).isEqualTo(5);
    assertThat(microsUntilDue(account)).isGreaterThan(VIEW_DELAY_LOWER_BOUND);

    view(account, products.get(4)).andExpect(status().isNoContent());
    assertThat(microsUntilDue(account)).isLessThanOrEqualTo(THREE_SECONDS_MICROS);
  }

  @Test
  void accountWithDeliveredOrderAlreadyMetTheThreshold() throws Exception {
    long account = account();
    orderIds.add(OrderFixtures.insertOrder(jdbcTemplate, account, "DELIVERED"));
    for (long product : products(5)) {
      view(account, product).andExpect(status().isNoContent());
    }

    assertThat(microsUntilDue(account)).isGreaterThan(VIEW_DELAY_LOWER_BOUND);
  }

  @Test
  void viewsOlderThanThirtyDaysAreNotCounted() throws Exception {
    long account = account();
    List<Long> products = products(5);
    for (int i = 0; i < 4; i++) {
      jdbcTemplate.update(
          "INSERT INTO product_view_log (account_id, product_id, viewed_at) VALUES (?, ?, ?)",
          account,
          products.get(i),
          LocalDateTime.now().minusDays(31));
    }

    view(account, products.get(4)).andExpect(status().isNoContent());

    assertThat(microsUntilDue(account)).isGreaterThan(VIEW_DELAY_LOWER_BOUND);
  }

  @Test
  void deletedProductOrMissingAccountGives404AndWritesNothing() throws Exception {
    long account = account();
    long deleted = product();
    productService.delete(deleted);

    view(account, deleted)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Không tìm thấy sản phẩm"));
    long missingAccount =
        jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM account", Long.class);
    view(missingAccount, product())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Không tìm thấy tài khoản"));

    assertThat(viewCount(account)).isZero();
    assertThat(requestCount(account)).isZero();
    assertThat(
            namedJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_view_log WHERE product_id IN (:ids)",
                Map.of("ids", productIds),
                Long.class))
        .isZero();
  }

  // ---------- RecomputeRequestService ----------

  @Test
  void requestNeedsAReadWriteTransaction() {
    long account = account();
    assertThatThrownBy(
            () ->
                recomputeRequestService.request(
                    account, RecomputeTask.RECOMMENDATION, RecomputeRequestService.VIEW_DELAY))
        .isInstanceOf(IllegalStateException.class);

    TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
    readOnly.setReadOnly(true);
    assertThatThrownBy(
            () ->
                readOnly.executeWithoutResult(
                    status ->
                        recomputeRequestService.request(
                            account,
                            RecomputeTask.RECOMMENDATION,
                            RecomputeRequestService.VIEW_DELAY)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(requestCount(account)).isZero();
  }

  @Test
  void requestsOfOneTransactionAreMergedKeepingTheShortestDelay() {
    long account = account();

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              recomputeRequestService.request(
                  account, RecomputeTask.RECOMMENDATION, RecomputeRequestService.VIEW_DELAY);
              recomputeRequestService.request(
                  account,
                  RecomputeTask.RECOMMENDATION,
                  RecomputeRequestService.STRONG_ACTION_DELAY);
              recomputeRequestService.request(
                  account, RecomputeTask.SEGMENT_ASSIGN, RecomputeRequestService.VIEW_DELAY);
            });

    assertThat(requestCount(account)).isEqualTo(2);
    assertThat(version(account)).isEqualTo(1);
    assertThat(microsUntilDue(account)).isLessThanOrEqualTo(THREE_SECONDS_MICROS);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT TIMESTAMPDIFF(MICROSECOND, UTC_TIMESTAMP(3), due_at)"
                    + " FROM recompute_request WHERE account_id = ? AND task_type = 'SEGMENT_ASSIGN'",
                Long.class,
                account))
        .isGreaterThan(VIEW_DELAY_LOWER_BOUND);
  }

  @Test
  void rolledBackTransactionWritesNoRequest() {
    long account = account();
    long other = account();
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    TransactionTemplate inner = new TransactionTemplate(transactionManager);
    inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    outer.executeWithoutResult(
        status -> {
          recomputeRequestService.request(
              account, RecomputeTask.RECOMMENDATION, RecomputeRequestService.VIEW_DELAY);
          // Giao dịch mới lồng bên trong có tập yêu cầu riêng và commit độc lập
          inner.executeWithoutResult(
              innerStatus ->
                  recomputeRequestService.request(
                      other, RecomputeTask.RECOMMENDATION, RecomputeRequestService.VIEW_DELAY));
          status.setRollbackOnly();
        });

    assertThat(requestCount(account)).isZero();
    assertThat(requestCount(other)).isEqualTo(1);
  }

  // ---------- TransactionRetry ----------

  @Test
  void transactionRetryRunsTheWholeTransactionAgainAfterADeadlock() {
    long account = account();
    AtomicInteger attempts = new AtomicInteger();

    String result =
        transactionRetry.execute(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                recomputeRequestService.request(
                    account,
                    RecomputeTask.RECOMMENDATION,
                    RecomputeRequestService.STRONG_ACTION_DELAY);
                throw deadlock();
              }
              recomputeRequestService.request(
                  account, RecomputeTask.RECOMMENDATION, RecomputeRequestService.VIEW_DELAY);
              return "xong";
            });

    assertThat(result).isEqualTo("xong");
    assertThat(attempts).hasValue(2);
    // Yêu cầu của lần chạy lỗi đã bị hủy theo giao dịch
    assertThat(version(account)).isEqualTo(1);
    assertThat(microsUntilDue(account)).isGreaterThan(VIEW_DELAY_LOWER_BOUND);
  }

  @Test
  void transactionRetryAlsoRetriesALockWaitTimeout() {
    AtomicInteger attempts = new AtomicInteger();

    String result =
        transactionRetry.execute(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw lockWaitTimeout();
              }
              return "xong";
            });

    assertThat(result).isEqualTo("xong");
    assertThat(attempts).hasValue(2);
  }

  @Test
  void transactionRetryGivesUpAfterThreeAttemptsAndNeverRetriesOtherErrors() {
    AtomicInteger deadlocks = new AtomicInteger();
    assertThatThrownBy(
            () ->
                transactionRetry.run(
                    () -> {
                      deadlocks.incrementAndGet();
                      throw deadlock();
                    }))
        .isInstanceOf(CannotAcquireLockException.class);
    assertThat(deadlocks).hasValue(3);

    AtomicInteger failures = new AtomicInteger();
    assertThatThrownBy(
            () ->
                transactionRetry.run(
                    () -> {
                      failures.incrementAndGet();
                      throw new IllegalArgumentException("Lỗi nghiệp vụ");
                    }))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(failures).hasValue(1);
  }

  @Test
  void transactionRetryRefusesToRunInsideAnExistingTransaction() {
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    assertThatThrownBy(() -> outer.executeWithoutResult(status -> transactionRetry.run(() -> {})))
        .isInstanceOf(IllegalStateException.class);
  }

  // ---------- Hàm hỗ trợ ----------

  private ResultActions view(long accountId, long productId) throws Exception {
    return mockMvc.perform(
        post("/api/products/" + productId + "/views")
            .with(
                jwt()
                    .jwt(j -> j.subject(String.valueOf(accountId)))
                    .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

  private long account() {
    Account account =
        accountRepository.save(
            new Account(
                "luot-xem-" + UUID.randomUUID() + "@test.local",
                "khong-dung",
                "Khách thử",
                Role.CUSTOMER));
    accountIds.add(account.getId());
    return account.getId();
  }

  private long product() {
    long id =
        productService
            .create(
                new ProductCreateRequest(
                    "Sản phẩm lượt xem " + UUID.randomUUID(),
                    null,
                    null,
                    null,
                    categoryId,
                    null,
                    List.of(),
                    List.of(new VariantRequest(null, null, 100000L, 5, null))))
            .id();
    productIds.add(id);
    return id;
  }

  private List<Long> products(int count) {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(product());
    }
    return ids;
  }

  private long viewCount(long accountId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM product_view_log WHERE account_id = ?", Long.class, accountId);
  }

  private long requestCount(long accountId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM recompute_request WHERE account_id = ?", Long.class, accountId);
  }

  /** Dòng yêu cầu tính lại gợi ý (RECOMMENDATION) của tài khoản. */
  private Map<String, Object> request(long accountId) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM recompute_request WHERE account_id = ? AND task_type = 'RECOMMENDATION'",
        accountId);
  }

  private int version(long accountId) {
    return ((Number) request(accountId).get("version")).intValue();
  }

  /** Số micro giây từ bây giờ (giờ UTC của MySQL) đến lúc yêu cầu tính lại gợi ý đến hạn. */
  private long microsUntilDue(long accountId) {
    return jdbcTemplate.queryForObject(
        "SELECT TIMESTAMPDIFF(MICROSECOND, UTC_TIMESTAMP(3), due_at) FROM recompute_request"
            + " WHERE account_id = ? AND task_type = 'RECOMMENDATION'",
        Long.class,
        accountId);
  }

  private void deleteWhere(String sql, List<Long> ids) {
    if (!ids.isEmpty()) {
      namedJdbcTemplate.update(sql, Map.of("ids", ids));
    }
  }

  private static CannotAcquireLockException deadlock() {
    return new CannotAcquireLockException(
        "Khóa chéo giả lập",
        new SQLException("Deadlock found when trying to get lock", "40001", 1213));
  }

  private static CannotAcquireLockException lockWaitTimeout() {
    return new CannotAcquireLockException(
        "Chờ khóa quá lâu giả lập",
        new SQLException("Lock wait timeout exceeded; try restarting transaction", "HY000", 1205));
  }
}
