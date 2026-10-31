package com.manhdn.fashionshop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.ProductResponse;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.ProductService;
import com.manhdn.fashionshop.service.VoucherService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Kiểm tra mã giảm giá (US-21, US-22, BR-30 đến BR-33) với MySQL thật: Quản trị viên quản lý mã,
 * khách áp mã ở bước xem trước và khi đặt hàng, hủy đơn trả lại lượt dùng.
 *
 * <p>Không dùng {@code @Transactional}: lượt dùng được trừ bằng câu UPDATE có điều kiện trong giao
 * dịch đặt hàng thật. Mọi dòng test tạo ra được xóa ở {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VoucherIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private ProductService productService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;

  private final List<Long> accountIds = new ArrayList<>();
  private final List<Long> productIds = new ArrayList<>();
  private final List<Long> voucherIds = new ArrayList<>();
  private long categoryId;
  private LocalDateTime now;

  @BeforeEach
  void setUp() {
    categoryId = categoryService.create("Danh mục mã giảm giá " + UUID.randomUUID()).id();
    now = LocalDateTime.now(VoucherService.ZONE).truncatedTo(ChronoUnit.SECONDS);
  }

  @AfterEach
  void cleanUp() {
    deleteWhere(
        "DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE account_id IN (:ids))",
        accountIds);
    deleteWhere("DELETE FROM orders WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM voucher WHERE id IN (:ids)", voucherIds);
    deleteWhere("DELETE FROM address WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM recompute_request WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM product_event_log WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM cart_item WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM product_variant WHERE product_id IN (:ids)", productIds);
    deleteWhere("DELETE FROM product WHERE id IN (:ids)", productIds);
    jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
    deleteWhere("DELETE FROM account WHERE id IN (:ids)", accountIds);
  }

  @Test
  void adminCreatesListsUpdatesAndTurnsOffVoucher() throws Exception {
    String code = uniqueCode();
    String body =
        performAdmin(
                post("/api/admin/vouchers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        voucherJson(
                            code.toLowerCase(),
                            "PERCENT",
                            10,
                            "200000",
                            "20000",
                            now.minusDays(1),
                            now.plusDays(7),
                            "100",
                            "1")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.discountType").value("PERCENT"))
            .andExpect(jsonPath("$.maxDiscountAmount").value(20_000))
            .andExpect(jsonPath("$.usedCount").value(0))
            .andExpect(jsonPath("$.remaining").value(100))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = ((Number) JsonPath.read(body, "$.id")).longValue();
    voucherIds.add(id);

    // Trùng mã (không phân biệt hoa thường) thì 409.
    performAdmin(
            post("/api/admin/vouchers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    voucherJson(
                        code.toLowerCase(),
                        "FIXED",
                        10_000,
                        null,
                        null,
                        now,
                        now.plusDays(1),
                        null,
                        null)))
        .andExpect(status().isConflict());

    performAdmin(get("/api/admin/vouchers").param("keyword", code.substring(0, 8)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].code").value(code));

    // Sửa thành mã giảm cố định, chưa tới ngày áp dụng, không giới hạn lượt.
    performAdmin(
            put("/api/admin/vouchers/%d".formatted(id))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    voucherJson(
                        code,
                        "FIXED",
                        30_000,
                        "0",
                        null,
                        now.plusDays(1),
                        now.plusDays(3),
                        null,
                        null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.discountValue").value(30_000))
        .andExpect(jsonPath("$.minOrderAmount").doesNotExist())
        .andExpect(jsonPath("$.remaining").doesNotExist())
        .andExpect(jsonPath("$.status").value("SCHEDULED"));

    performAdmin(
            put("/api/admin/vouchers/%d/active".formatted(id))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.status").value("DISABLED"));

    performAdmin(
            put("/api/admin/vouchers/%d/active".formatted(Long.MAX_VALUE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": true}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void invalidVouchersAreRejected() throws Exception {
    // Phần trăm trên 100.
    expectBadRequest(
        voucherJson(uniqueCode(), "PERCENT", 150, null, null, now, now.plusDays(1), null, null));
    // Giảm cố định không có trần.
    expectBadRequest(
        voucherJson(uniqueCode(), "FIXED", 10_000, null, "5000", now, now.plusDays(1), null, null));
    // Hết hạn trước khi bắt đầu.
    expectBadRequest(
        voucherJson(uniqueCode(), "FIXED", 10_000, null, null, now, now.minusDays(1), null, null));
    // Lượt mỗi khách nhiều hơn tổng lượt.
    expectBadRequest(
        voucherJson(uniqueCode(), "FIXED", 10_000, null, null, now, now.plusDays(1), "2", "3"));
    // Mã có dấu cách hoặc chữ có dấu.
    expectBadRequest(
        voucherJson("GIẢM GIÁ", "FIXED", 10_000, null, null, now, now.plusDays(1), null, null));
    performAdmin(get("/api/admin/vouchers").param("size", "51")).andExpect(status().isBadRequest());
    assertThat(voucherIds).isEmpty();
  }

  @Test
  void onlyAdminManagesVouchers() throws Exception {
    long account = account();
    perform(account, get("/api/admin/vouchers")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/vouchers")).andExpect(status().isUnauthorized());
  }

  @Test
  void previewAppliesValidCodeAndExplainsWhyOthersFail() throws Exception {
    long account = account();
    addToCart(account, variantOf(product(120_000L, 5)), 2);

    String fixed =
        voucher("FIXED", 50_000, "200000", null, now.minusDays(1), now.plusDays(1), null, null);
    perform(account, get("/api/orders/preview").param("voucherCode", " " + fixed.toLowerCase()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subtotal").value(240_000))
        .andExpect(jsonPath("$.shippingFee").value(30_000))
        .andExpect(jsonPath("$.discount").value(50_000))
        .andExpect(jsonPath("$.total").value(220_000))
        .andExpect(jsonPath("$.voucherCode").value(fixed))
        .andExpect(jsonPath("$.voucherError").doesNotExist());

    // Giảm 20% nhưng trần 30.000đ.
    String percent =
        voucher("PERCENT", 20, null, "30000", now.minusDays(1), now.plusDays(1), null, null);
    perform(account, get("/api/orders/preview").param("voucherCode", percent))
        .andExpect(jsonPath("$.discount").value(30_000))
        .andExpect(jsonPath("$.total").value(240_000));

    expectPreviewError(account, "KHONG-CO-MA-NAY", "Mã giảm giá không đúng");
    expectPreviewError(
        account,
        voucher("FIXED", 10_000, "500000", null, now.minusDays(1), now.plusDays(1), null, null),
        "còn thiếu 260.000đ");
    expectPreviewError(
        account,
        voucher("FIXED", 10_000, null, null, now.minusDays(3), now.minusDays(1), null, null),
        "đã hết hạn");
    expectPreviewError(
        account,
        voucher("FIXED", 10_000, null, null, now.plusDays(1), now.plusDays(2), null, null),
        "áp dụng từ");

    String disabled =
        voucher("FIXED", 10_000, null, null, now.minusDays(1), now.plusDays(1), null, null);
    jdbcTemplate.update("UPDATE voucher SET is_active = FALSE WHERE code = ?", disabled);
    expectPreviewError(account, disabled, "đã ngừng áp dụng");

    perform(account, get("/api/orders/preview").param("voucherCode", "x".repeat(51)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void placingOrderWithVoucherUsesItOnceAndCancellingGivesItBack() throws Exception {
    long account = account();
    long address = address(account);
    addToCart(account, variantOf(product(120_000L, 5)), 2);
    String code =
        voucher("FIXED", 50_000, "200000", null, now.minusDays(1), now.plusDays(1), "10", "1");

    // Tổng khách thấy không tính mã thì 409, không trừ lượt.
    perform(account, placeOrder(address, 270_000, code))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").exists());
    assertThat(usedCount(code)).isZero();

    String body =
        perform(account, placeOrder(address, 220_000, code.toLowerCase()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subtotal").value(240_000))
            .andExpect(jsonPath("$.discount").value(50_000))
            .andExpect(jsonPath("$.voucherCode").value(code))
            .andExpect(jsonPath("$.total").value(220_000))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long orderId = ((Number) JsonPath.read(body, "$.id")).longValue();
    assertThat(usedCount(code)).isEqualTo(1);

    perform(account, get("/api/orders/%d".formatted(orderId)))
        .andExpect(jsonPath("$.voucherCode").value(code));

    // Mỗi khách 1 lượt (BR-31): lần sau báo lỗi, đặt thì 409.
    addToCart(account, variantOf(product(250_000L, 5)), 1);
    expectPreviewError(account, code, "Bạn đã dùng hết lượt của mã này");
    perform(account, placeOrder(address, 260_000, code))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("Hãy bỏ mã")));

    // Hủy đơn trả lại lượt dùng (BR-33).
    perform(account, post("/api/orders/%d/cancel".formatted(orderId))).andExpect(status().isOk());
    assertThat(usedCount(code)).isZero();
    perform(account, get("/api/orders/preview").param("voucherCode", code))
        .andExpect(jsonPath("$.discount").value(50_000))
        .andExpect(jsonPath("$.voucherError").doesNotExist());
  }

  @Test
  void voucherStopsAtTotalUsageLimit() throws Exception {
    long first = account();
    long second = account();
    long product = product(100_000L, 10);
    addToCart(first, variantOf(product), 1);
    addToCart(second, variantOf(product), 1);
    String code =
        voucher("FIXED", 20_000, null, null, now.minusDays(1), now.plusDays(1), "1", null);

    perform(first, placeOrder(address(first), 110_000, code)).andExpect(status().isCreated());
    expectPreviewError(second, code, "đã hết lượt dùng");
    perform(second, placeOrder(address(second), 110_000, code)).andExpect(status().isConflict());
    assertThat(usedCount(code)).isEqualTo(1);

    // Lượt cuối bị lấy ngay trước khi đặt (BR-30): câu UPDATE có điều kiện không vượt giới hạn.
    jdbcTemplate.update("UPDATE voucher SET usage_limit_total = 2 WHERE code = ?", code);
    perform(second, get("/api/orders/preview").param("voucherCode", code))
        .andExpect(jsonPath("$.voucherError").doesNotExist());
    jdbcTemplate.update("UPDATE voucher SET used_count = 2 WHERE code = ?", code);
    perform(second, placeOrder(address(second), 110_000, code))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("hết lượt dùng")));
  }

  @Test
  void discountNeverTakesGoodsBelowFiveThousand() throws Exception {
    long account = account();
    addToCart(account, variantOf(product(120_000L, 5)), 1);
    String code =
        voucher("FIXED", 1_000_000, null, null, now.minusDays(1), now.plusDays(1), null, null);

    // Giảm tối đa 120.000 - 5.000 = 115.000đ (BR-32); phí giao hàng tính trên tiền hàng trước giảm.
    perform(account, get("/api/orders/preview").param("voucherCode", code))
        .andExpect(jsonPath("$.discount").value(115_000))
        .andExpect(jsonPath("$.shippingFee").value(30_000))
        .andExpect(jsonPath("$.total").value(35_000));
  }

  private void expectBadRequest(String json) throws Exception {
    performAdmin(post("/api/admin/vouchers").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest());
  }

  private void expectPreviewError(long account, String code, String error) throws Exception {
    perform(account, get("/api/orders/preview").param("voucherCode", code))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.discount").value(0))
        .andExpect(jsonPath("$.voucherCode").doesNotExist())
        .andExpect(jsonPath("$.voucherError", containsString(error)));
  }

  private String voucher(
      String type,
      long value,
      String minOrder,
      String maxDiscount,
      LocalDateTime from,
      LocalDateTime until,
      String limitTotal,
      String limitPerAccount)
      throws Exception {
    String code = uniqueCode();
    String body =
        performAdmin(
                post("/api/admin/vouchers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        voucherJson(
                            code,
                            type,
                            value,
                            minOrder,
                            maxDiscount,
                            from,
                            until,
                            limitTotal,
                            limitPerAccount)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    voucherIds.add(((Number) JsonPath.read(body, "$.id")).longValue());
    return code;
  }

  private static String voucherJson(
      String code,
      String type,
      long value,
      String minOrder,
      String maxDiscount,
      LocalDateTime from,
      LocalDateTime until,
      String limitTotal,
      String limitPerAccount) {
    return ("{\"code\": \"%s\", \"discountType\": \"%s\", \"discountValue\": %d,"
            + " \"minOrderAmount\": %s, \"maxDiscountAmount\": %s, \"validFrom\": \"%s\","
            + " \"validUntil\": \"%s\", \"usageLimitTotal\": %s, \"usageLimitPerAccount\": %s}")
        .formatted(
            code, type, value, minOrder, maxDiscount, from, until, limitTotal, limitPerAccount);
  }

  private static String uniqueCode() {
    return "TEST-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase();
  }

  private int usedCount(String code) {
    return jdbcTemplate.queryForObject(
        "SELECT used_count FROM voucher WHERE code = ?", Integer.class, code);
  }

  private static MockHttpServletRequestBuilder placeOrder(long addressId, long total, String code) {
    return post("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            ("{\"idempotencyKey\": \"%s\", \"paymentMethod\": \"COD\", \"addressId\": %d,"
                    + " \"expectedTotal\": %d, \"voucherCode\": \"%s\"}")
                .formatted(UUID.randomUUID(), addressId, total, code));
  }

  private void addToCart(long account, long variant, int quantity) throws Exception {
    perform(
            account,
            post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\": %d, \"quantity\": %d}".formatted(variant, quantity)))
        .andExpect(status().isOk());
  }

  private long address(long account) throws Exception {
    String body =
        perform(
                account,
                post("/api/addresses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"recipientName\": \"Khách thử\", \"phone\": \"0901234567\","
                            + " \"addressDetail\": \"1 Võ Văn Ngân, Thủ Đức\", \"isDefault\": true}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private ResultActions performAdmin(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.with(
            jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private ResultActions perform(long accountId, MockHttpServletRequestBuilder request)
      throws Exception {
    return mockMvc.perform(
        request.with(
            jwt()
                .jwt(j -> j.subject(String.valueOf(accountId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

  private long account() {
    Account account =
        accountRepository.save(
            new Account(
                "ma-giam-gia-" + UUID.randomUUID() + "@test.local",
                "khong-dung",
                "Khách thử",
                Role.CUSTOMER));
    accountIds.add(account.getId());
    return account.getId();
  }

  private long product(long price, int stock) {
    ProductResponse product =
        productService.create(
            new ProductCreateRequest(
                "Sản phẩm mã giảm giá " + UUID.randomUUID(),
                null,
                null,
                null,
                categoryId,
                null,
                List.of(),
                List.of(new VariantRequest(null, null, price, stock, null))));
    productIds.add(product.id());
    return product.id();
  }

  private long variantOf(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM product_variant WHERE product_id = ?", Long.class, productId);
  }

  private void deleteWhere(String sql, List<Long> ids) {
    if (!ids.isEmpty()) {
      namedJdbcTemplate.update(sql, Map.of("ids", ids));
    }
  }
}
