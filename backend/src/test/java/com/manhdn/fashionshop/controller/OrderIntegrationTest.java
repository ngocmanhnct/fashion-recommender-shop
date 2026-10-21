package com.manhdn.fashionshop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.manhdn.fashionshop.dto.PlaceOrderRequest;
import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.ProductResponse;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.PaymentMethod;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.OrderService;
import com.manhdn.fashionshop.service.ProductService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Kiểm tra API sổ địa chỉ, đặt hàng, đơn hàng (US-09, US-10, US-11, US-20; BR-08 đến BR-16, BR-29,
 * BR-35) với MySQL thật.
 *
 * <p>Không dùng {@code @Transactional}: test đặt hàng đồng thời cần các giao dịch thật, và yêu cầu
 * tính lại gợi ý chỉ được ghi ngay trước khi giao dịch commit. Mọi dòng test tạo ra được xóa ở
 * {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private ProductService productService;
  @Autowired private OrderService orderService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;

  private final List<Long> accountIds = new ArrayList<>();
  private final List<Long> productIds = new ArrayList<>();
  private long categoryId;

  @BeforeEach
  void createCategory() {
    categoryId = categoryService.create("Danh mục đặt hàng " + UUID.randomUUID()).id();
  }

  @AfterEach
  void cleanUp() {
    deleteWhere(
        "DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE account_id IN (:ids))",
        accountIds);
    deleteWhere("DELETE FROM orders WHERE account_id IN (:ids)", accountIds);
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
  void placingCodOrderSnapshotsCartDeductsStockClearsCartAndIsIdempotent() throws Exception {
    long account = account();
    long address = address(account, "Nguyễn Văn A", "0901234567", "1 Võ Văn Ngân, Thủ Đức");
    long product = product(120_000L, 5);
    long variant = variantOf(product);
    addToCart(account, variant, 2);

    perform(account, get("/api/orders/preview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.subtotal").value(240_000))
        .andExpect(jsonPath("$.shippingFee").value(30_000))
        .andExpect(jsonPath("$.total").value(270_000));

    String key = UUID.randomUUID().toString();
    String body =
        perform(account, placeOrder(key, address, 270_000))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.paymentMethod").value("COD"))
            .andExpect(jsonPath("$.paymentStatus").doesNotExist())
            .andExpect(jsonPath("$.recipientName").value("Nguyễn Văn A"))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unitPrice").value(120_000))
            .andExpect(jsonPath("$.subtotal").value(240_000))
            .andExpect(jsonPath("$.shippingFee").value(30_000))
            .andExpect(jsonPath("$.total").value(270_000))
            .andExpect(jsonPath("$.cancellable").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long orderId = ((Number) JsonPath.read(body, "$.id")).longValue();

    assertThat(stockOf(variant)).isEqualTo(3);
    assertThat(count("SELECT COUNT(*) FROM cart_item WHERE account_id = ?", account)).isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM recompute_request WHERE account_id = ?"
                    + " AND task_type = 'RECOMMENDATION'",
                account))
        .isEqualTo(1);

    // Bấm lại cùng mã: trả đúng đơn cũ, không tạo đơn mới, không trừ tồn kho lần nữa (BR-10).
    perform(account, placeOrder(key, address, 270_000))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orderId));
    assertThat(count("SELECT COUNT(*) FROM orders WHERE account_id = ?", account)).isEqualTo(1);
    assertThat(stockOf(variant)).isEqualTo(3);

    // Mã này thuộc tài khoản khác thì không trả đơn của người khác.
    long other = account();
    perform(other, placeOrder(key, address(other, "B", "0912345678", "Hà Nội"), 270_000))
        .andExpect(status().isConflict());

    // Đổi tên, đổi giá sản phẩm sau khi đặt: đơn vẫn hiện như lúc mua (BR-15).
    jdbcTemplate.update("UPDATE product SET name = 'Tên mới' WHERE id = ?", product);
    jdbcTemplate.update("UPDATE product_variant SET price = 1 WHERE id = ?", variant);
    perform(account, get("/api/orders/" + orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].productName").value(startsWith("Sản phẩm")))
        .andExpect(jsonPath("$.items[0].unitPrice").value(120_000));
    perform(other, get("/api/orders/" + orderId)).andExpect(status().isNotFound());
  }

  @Test
  void subtotalFromFiveHundredThousandShipsFree() throws Exception {
    long account = account();
    long address = address(account, "A", "0901234567", "TP HCM");
    addToCart(account, variantOf(product(250_000L, 5)), 2);

    perform(account, placeOrder(UUID.randomUUID().toString(), address, 500_000))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.shippingFee").value(0))
        .andExpect(jsonPath("$.total").value(500_000));
  }

  @Test
  void notEnoughStockOrChangedPriceRejectsWholeOrder() throws Exception {
    long account = account();
    long address = address(account, "A", "0901234567", "TP HCM");
    long enough = variantOf(product(100_000L, 5));
    long scarce = variantOf(product(50_000L, 5));
    addToCart(account, enough, 1);
    addToCart(account, scarce, 3);
    jdbcTemplate.update("UPDATE product_variant SET stock = 2 WHERE id = ?", scarce);

    perform(account, placeOrder(UUID.randomUUID().toString(), address, 280_000))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(containsString("chỉ còn 2 sản phẩm")));
    assertThat(count("SELECT COUNT(*) FROM orders WHERE account_id = ?", account)).isZero();
    assertThat(stockOf(enough)).isEqualTo(5);
    assertThat(count("SELECT COUNT(*) FROM cart_item WHERE account_id = ?", account)).isEqualTo(2);

    // Xem trước tự giảm dòng thiếu hàng (BR-11); sau đó giá đổi thì tổng cũ bị từ chối.
    perform(account, get("/api/orders/preview"))
        .andExpect(jsonPath("$.notices[0].type").value("QUANTITY_REDUCED"))
        .andExpect(jsonPath("$.total").value(230_000));
    jdbcTemplate.update("UPDATE product_variant SET price = 110000 WHERE id = ?", enough);
    perform(account, placeOrder(UUID.randomUUID().toString(), address, 230_000))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(containsString("240.000đ")));
    perform(account, placeOrder(UUID.randomUUID().toString(), address, 240_000))
        .andExpect(status().isCreated());
    assertThat(stockOf(enough)).isEqualTo(4);
    assertThat(stockOf(scarce)).isZero();
  }

  @Test
  void invalidOrdersAreRejected() throws Exception {
    long account = account();
    long address = address(account, "A", "0901234567", "TP HCM");
    long other = account();
    long othersAddress = address(other, "B", "0912345678", "Hà Nội");

    perform(account, placeOrder(UUID.randomUUID().toString(), address, 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Giỏ hàng đang trống"));
    addToCart(account, variantOf(product(100_000L, 5)), 1);
    perform(account, placeOrder(UUID.randomUUID().toString(), othersAddress, 130_000))
        .andExpect(status().isNotFound());
    perform(
            account,
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"idempotencyKey\": \"%s\", \"paymentMethod\": \"ONLINE\", \"addressId\": %d,"
                            .formatted(UUID.randomUUID(), address)
                        + " \"expectedTotal\": 130000}"))
        .andExpect(status().isBadRequest());
    perform(
            account,
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\": 1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.idempotencyKey").exists())
        .andExpect(jsonPath("$.errors.paymentMethod").exists());
    assertThat(count("SELECT COUNT(*) FROM orders WHERE account_id = ?", account)).isZero();
  }

  @Test
  void twoCustomersBuyingTheLastItemAtOnceSellOnlyOne() throws Exception {
    long variant = variantOf(product(100_000L, 1));
    List<long[]> buyers = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      long account = account();
      long address = address(account, "Người mua " + i, "0901234567", "TP HCM");
      addToCart(account, variant, 1);
      buyers.add(new long[] {account, address});
    }

    CountDownLatch start = new CountDownLatch(1);
    List<String> outcomes = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      List<Future<String>> results = new ArrayList<>();
      for (long[] buyer : buyers) {
        Callable<String> task =
            () -> {
              start.await();
              try {
                orderService.placeOrder(
                    buyer[0],
                    new PlaceOrderRequest(
                        UUID.randomUUID().toString(), PaymentMethod.COD, buyer[1], 130_000L));
                return "OK";
              } catch (ConflictException e) {
                return "CONFLICT";
              }
            };
        results.add(pool.submit(task));
      }
      start.countDown();
      for (Future<String> result : results) {
        outcomes.add(result.get());
      }
    }

    assertThat(outcomes).containsExactlyInAnyOrder("OK", "CONFLICT");
    assertThat(stockOf(variant)).isZero();
    assertThat(
            count(
                "SELECT COALESCE(SUM(quantity), 0) FROM order_item WHERE variant_id = ?", variant))
        .isEqualTo(1);
  }

  @Test
  void customerCancelsPendingOrderAndStockReturnsButNotOnceShipped() throws Exception {
    long account = account();
    long address = address(account, "A", "0901234567", "TP HCM");
    long variant = variantOf(product(100_000L, 5));
    addToCart(account, variant, 2);
    long orderId = placedOrderId(account, address, 230_000);
    assertThat(stockOf(variant)).isEqualTo(3);

    perform(account(), post("/api/orders/%d/cancel".formatted(orderId)))
        .andExpect(status().isNotFound());
    perform(account, post("/api/orders/%d/cancel".formatted(orderId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.cancellable").value(false));
    assertThat(stockOf(variant)).isEqualTo(5);
    // Hủy lại đơn đã hủy: không hoàn tồn kho lần nữa.
    perform(account, post("/api/orders/%d/cancel".formatted(orderId))).andExpect(status().isOk());
    assertThat(stockOf(variant)).isEqualTo(5);

    addToCart(account, variant, 1);
    long shipped = placedOrderId(account, address, 130_000);
    jdbcTemplate.update("UPDATE orders SET status = 'SHIPPING' WHERE id = ?", shipped);
    perform(account, post("/api/orders/%d/cancel".formatted(shipped)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(containsString("rời kho")));
    assertThat(stockOf(variant)).isEqualTo(4);

    perform(account, get("/api/orders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].id").value(shipped))
        .andExpect(jsonPath("$.content[0].status").value("SHIPPING"))
        .andExpect(jsonPath("$.content[0].totalQuantity").value(1))
        .andExpect(jsonPath("$.content[1].id").value(orderId))
        .andExpect(jsonPath("$.content[1].status").value("CANCELLED"));
    perform(account, get("/api/orders?size=0")).andExpect(status().isBadRequest());
  }

  @Test
  void addressBookKeepsExactlyOneDefault() throws Exception {
    long account = account();
    long first = address(account, "  Trần   Thị B ", "0901234567", "Quận 1", false);
    long second = address(account, "C", "0912345678", "Quận 3", true);

    perform(account, get("/api/addresses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(second))
        .andExpect(jsonPath("$[0].isDefault").value(true))
        .andExpect(jsonPath("$[1].recipientName").value("Trần Thị B"))
        .andExpect(jsonPath("$[1].isDefault").value(false));

    perform(
            account,
            put("/api/addresses/" + first)
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressJson("B", "0901234567", "Quận 1", true)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isDefault").value(true));
    perform(account, get("/api/addresses")).andExpect(jsonPath("$[0].id").value(first));

    // Xóa địa chỉ đã dùng cho đơn: đơn vẫn giữ bản sao, địa chỉ còn lại thành mặc định.
    addToCart(account, variantOf(product(100_000L, 5)), 1);
    long orderId = placedOrderId(account, first, 130_000);
    perform(account, delete("/api/addresses/" + first)).andExpect(status().isNoContent());
    perform(account, get("/api/addresses"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(second))
        .andExpect(jsonPath("$[0].isDefault").value(true));
    perform(account, get("/api/orders/" + orderId))
        .andExpect(jsonPath("$.recipientName").value("B"))
        .andExpect(jsonPath("$.addressDetail").value("Quận 1"));

    perform(account(), delete("/api/addresses/" + second)).andExpect(status().isNotFound());
    perform(
            account,
            post("/api/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressJson("A", "123", "Q1", false)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.phone").exists());
    for (int i = 0; i < 9; i++) {
      address(account, "A" + i, "0901234567", "Q" + i);
    }
    perform(
            account,
            post("/api/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressJson("A", "0901234567", "Q1", false)))
        .andExpect(status().isConflict());
  }

  @Test
  void adminMovesOrderForwardOneStepAndCanCancelUntilDelivered() throws Exception {
    long account = account();
    long address = address(account, "Trần Thị B", "0912345678", "Quận 1");
    long variant = variantOf(product(100_000L, 5));
    addToCart(account, variant, 2);
    long orderId = placedOrderId(account, address, 230_000);
    jdbcTemplate.update("DELETE FROM recompute_request WHERE account_id = ?", account);
    String email =
        jdbcTemplate.queryForObject(
            "SELECT email FROM account WHERE id = ?", String.class, account);

    performAdmin(get("/api/admin/orders?status=PENDING&keyword=" + email))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(orderId))
        .andExpect(jsonPath("$.content[0].customerEmail").value(email))
        .andExpect(jsonPath("$.content[0].totalQuantity").value(2));
    performAdmin(get("/api/admin/orders?status=SHIPPING&keyword=" + email))
        .andExpect(jsonPath("$.totalElements").value(0));
    performAdmin(get("/api/admin/orders?keyword=" + orderId))
        .andExpect(jsonPath("$.content[0].id").value(orderId));
    performAdmin(get("/api/admin/orders?status=XYZ")).andExpect(status().isBadRequest());
    performAdmin(get("/api/admin/orders/%d".formatted(orderId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.id").value(orderId))
        .andExpect(jsonPath("$.customerName").value("Khách thử"))
        .andExpect(jsonPath("$.nextStatus").value("PROCESSING"))
        .andExpect(jsonPath("$.adminCancellable").value(true));

    // Không nhảy cóc, không lùi trạng thái.
    performAdmin(changeStatus(orderId, "SHIPPING")).andExpect(status().isConflict());
    performAdmin(changeStatus(orderId, "PROCESSING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.status").value("PROCESSING"))
        .andExpect(jsonPath("$.nextStatus").value("SHIPPING"));
    performAdmin(changeStatus(orderId, "PENDING")).andExpect(status().isConflict());
    performAdmin(changeStatus(orderId, "SHIPPING")).andExpect(status().isOk());
    // Gửi lại đúng trạng thái hiện tại: không lỗi, không đổi gì.
    performAdmin(changeStatus(orderId, "SHIPPING")).andExpect(status().isOk());
    perform(account, post("/api/orders/%d/cancel".formatted(orderId)))
        .andExpect(status().isConflict());

    // Admin hủy được đơn Đang giao (khách từ chối nhận hàng COD), tồn kho được hoàn.
    performAdmin(changeStatus(orderId, "CANCELLED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.status").value("CANCELLED"))
        .andExpect(jsonPath("$.nextStatus").doesNotExist())
        .andExpect(jsonPath("$.adminCancellable").value(false));
    assertThat(stockOf(variant)).isEqualTo(5);
    performAdmin(changeStatus(orderId, "CANCELLED")).andExpect(status().isOk());
    assertThat(stockOf(variant)).isEqualTo(5);
    performAdmin(changeStatus(orderId, "PROCESSING")).andExpect(status().isConflict());

    // Đã giao: yêu cầu tính lại gợi ý và gán nhóm khách hàng; sau đó không hủy được nữa.
    addToCart(account, variant, 1);
    long delivered = placedOrderId(account, address, 130_000);
    jdbcTemplate.update("DELETE FROM recompute_request WHERE account_id = ?", account);
    for (String next : List.of("PROCESSING", "SHIPPING", "DELIVERED")) {
      performAdmin(changeStatus(delivered, next)).andExpect(status().isOk());
    }
    assertThat(count("SELECT COUNT(*) FROM recompute_request WHERE account_id = ?", account))
        .isEqualTo(2);
    performAdmin(changeStatus(delivered, "CANCELLED"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(containsString("đã giao")));
    assertThat(stockOf(variant)).isEqualTo(4);

    performAdmin(changeStatus(Long.MAX_VALUE, "PROCESSING")).andExpect(status().isNotFound());
    performAdmin(
            put("/api/admin/orders/%d/status".formatted(delivered))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    perform(account, get("/api/admin/orders")).andExpect(status().isForbidden());
  }

  @Test
  void ordersAndAddressesNeedCustomerToken() throws Exception {
    mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/orders/preview")
                .with(
                    jwt()
                        .jwt(j -> j.subject("1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  private long placedOrderId(long account, long address, long expectedTotal) throws Exception {
    String body =
        perform(account, placeOrder(UUID.randomUUID().toString(), address, expectedTotal))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private MockHttpServletRequestBuilder placeOrder(String key, long addressId, long total) {
    return post("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            ("{\"idempotencyKey\": \"%s\", \"paymentMethod\": \"COD\", \"addressId\": %d,"
                    + " \"expectedTotal\": %d}")
                .formatted(key, addressId, total));
  }

  private void addToCart(long account, long variant, int quantity) throws Exception {
    perform(
            account,
            post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\": %d, \"quantity\": %d}".formatted(variant, quantity)))
        .andExpect(status().isOk());
  }

  private long address(long account, String name, String phone, String detail) throws Exception {
    return address(account, name, phone, detail, false);
  }

  private long address(long account, String name, String phone, String detail, boolean isDefault)
      throws Exception {
    String body =
        perform(
                account,
                post("/api/addresses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(addressJson(name, phone, detail, isDefault)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private static String addressJson(String name, String phone, String detail, boolean isDefault) {
    return ("{\"recipientName\": \"%s\", \"phone\": \"%s\", \"addressDetail\": \"%s\","
            + " \"isDefault\": %b}")
        .formatted(name, phone, detail, isDefault);
  }

  private static MockHttpServletRequestBuilder changeStatus(long orderId, String status) {
    return put("/api/admin/orders/%d/status".formatted(orderId))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\": \"%s\"}".formatted(status));
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
                "dat-hang-" + UUID.randomUUID() + "@test.local",
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
                "Sản phẩm đặt hàng " + UUID.randomUUID(),
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

  private int stockOf(long variantId) {
    return jdbcTemplate.queryForObject(
        "SELECT stock FROM product_variant WHERE id = ?", Integer.class, variantId);
  }

  private long count(String sql, Object... args) {
    return jdbcTemplate.queryForObject(sql, Long.class, args);
  }

  private void deleteWhere(String sql, List<Long> ids) {
    if (!ids.isEmpty()) {
      namedJdbcTemplate.update(sql, Map.of("ids", ids));
    }
  }
}
