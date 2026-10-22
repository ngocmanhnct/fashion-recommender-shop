package com.manhdn.fashionshop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.manhdn.fashionshop.client.VnPayClient;
import com.manhdn.fashionshop.client.VnPayClient.RefundResult;
import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.ProductResponse;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.PaymentService;
import com.manhdn.fashionshop.service.ProductService;
import com.manhdn.fashionshop.service.RefundService;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Kiểm tra thanh toán VNPay (US-10, BR-28, BR-34) với MySQL thật: tạo đường dẫn thanh toán, kết quả
 * VNPay gửi về (trang trả về và IPN, chữ ký HMAC-SHA512), hoàn tiền khi hủy đơn đã thanh toán, tự
 * hủy đơn quá hạn. API hoàn tiền của VNPay được giả lập; chữ ký được tính độc lập trong test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrationTest {

  /** Khóa bí mật giả trong src/test/resources/config/application.properties. */
  private static final String SECRET = "khoa-gia-chi-dung-trong-test";

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private ProductService productService;
  @Autowired private PaymentService paymentService;
  @Autowired private RefundService refundService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;
  @MockitoSpyBean private VnPayClient vnPayClient;

  private final List<Long> accountIds = new ArrayList<>();
  private final List<Long> productIds = new ArrayList<>();
  private long categoryId;

  @BeforeEach
  void createCategory() {
    categoryId = categoryService.create("Danh mục thanh toán " + UUID.randomUUID()).id();
  }

  @AfterEach
  void cleanUp() {
    String orders = "SELECT id FROM orders WHERE account_id IN (:ids)";
    deleteWhere("DELETE FROM refund WHERE order_id IN (" + orders + ")", accountIds);
    deleteWhere("DELETE FROM order_item WHERE order_id IN (" + orders + ")", accountIds);
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
  void onlineOrderIsPaidThroughSignedVnPayCallbacksOnlyOnce() throws Exception {
    long account = account();
    long variant = variantOf(product(200_000L, 5));
    Order order = placeOnline(account, variant, 2, 430_000);

    perform(account, get("/api/orders/preview"))
        .andExpect(jsonPath("$.onlinePaymentAvailable").value(true));
    perform(account, get("/api/orders/%d".formatted(order.id)))
        .andExpect(jsonPath("$.paymentMethod").value("ONLINE"))
        .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
        .andExpect(jsonPath("$.paymentDeadline").exists());
    assertThat(stockOf(variant)).isEqualTo(3);

    String url = paymentUrl(account, order.id);
    Map<String, String> sent = queryOf(url);
    assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
    assertThat(sent)
        .containsEntry("vnp_Amount", "43000000")
        .containsEntry("vnp_TmnCode", "TESTCODE")
        .containsEntry("vnp_CurrCode", "VND");
    assertThat(sent.get("vnp_TxnRef"))
        .startsWith(String.valueOf(order.id))
        .hasSize(String.valueOf(order.id).length() + 14);
    // Chữ ký trên URL đúng như VNPay sẽ kiểm tra lại.
    Map<String, String> unsigned = new TreeMap<>(sent);
    unsigned.remove("vnp_SecureHash");
    assertThat(sent.get("vnp_SecureHash")).isEqualTo(hmac(encode(unsigned)));
    perform(account(), post("/api/orders/%d/payment-url".formatted(order.id)))
        .andExpect(status().isNotFound());

    // Khách bấm hủy ở trang VNPay (mã 24): đơn vẫn chờ thanh toán, admin chưa xử lý được.
    Map<String, String> cancelled = callback(sent, "24", "02");
    mockMvc
        .perform(get(URI.create("/api/payments/vnpay/return" + query(cancelled))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paid").value(false))
        .andExpect(jsonPath("$.orderId").value(order.id));
    performAdmin(get("/api/admin/orders/%d".formatted(order.id)))
        .andExpect(jsonPath("$.order.paymentStatus").value("PENDING"))
        .andExpect(jsonPath("$.nextStatus").doesNotExist());
    performAdmin(changeStatus(order.id, "PROCESSING")).andExpect(status().isConflict());

    // Dữ liệu giả mạo hoặc sai lệch bị từ chối theo mã của VNPay.
    Map<String, String> success = callback(queryOf(paymentUrl(account, order.id)), "00", "00");
    Map<String, String> forged = new TreeMap<>(success);
    forged.put("vnp_SecureHash", "00" + success.get("vnp_SecureHash").substring(2));
    ipn(forged).andExpect(jsonPath("$.RspCode").value("97"));
    Map<String, String> wrongAmount = new TreeMap<>(success);
    wrongAmount.put("vnp_Amount", "100");
    ipn(signed(wrongAmount)).andExpect(jsonPath("$.RspCode").value("04"));
    Map<String, String> unknown = new TreeMap<>(success);
    unknown.put("vnp_TxnRef", Long.MAX_VALUE / 100 + "20261021120000");
    ipn(signed(unknown)).andExpect(jsonPath("$.RspCode").value("01"));

    ipn(success).andExpect(jsonPath("$.RspCode").value("00"));
    ipn(success).andExpect(jsonPath("$.RspCode").value("02"));
    mockMvc
        .perform(get(URI.create("/api/payments/vnpay/return" + query(success))))
        .andExpect(jsonPath("$.paid").value(true));
    perform(account, get("/api/orders/%d".formatted(order.id)))
        .andExpect(jsonPath("$.paymentStatus").value("PAID"))
        .andExpect(jsonPath("$.paidAt").exists())
        .andExpect(jsonPath("$.paymentDeadline").doesNotExist());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payment_transaction_no FROM orders WHERE id = ?", String.class, order.id))
        .isEqualTo("14000001");
    perform(account, post("/api/orders/%d/payment-url".formatted(order.id)))
        .andExpect(status().isConflict());
    performAdmin(changeStatus(order.id, "PROCESSING")).andExpect(status().isOk());
  }

  @Test
  void cancellingPaidOrderRefundsOnceRetriesThenNeedsManualHandling() throws Exception {
    long account = account();
    long variant = variantOf(product(100_000L, 5));
    Order order = placeOnline(account, variant, 1, 130_000);
    ipn(callback(queryOf(paymentUrl(account, order.id)), "00", "00"))
        .andExpect(jsonPath("$.RspCode").value("00"));

    perform(account, post("/api/orders/%d/cancel".formatted(order.id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.paymentStatus").value("PAID"))
        .andExpect(jsonPath("$.refund.status").value("PROCESSING"))
        .andExpect(jsonPath("$.refund.amount").value(130_000));
    perform(account, post("/api/orders/%d/cancel".formatted(order.id))).andExpect(status().isOk());
    assertThat(count("SELECT COUNT(*) FROM refund WHERE order_id = ?", order.id)).isEqualTo(1);
    assertThat(stockOf(variant)).isEqualTo(5);

    doReturn(RefundResult.failed("Lỗi giả lập")).when(vnPayClient).refund(any());
    for (int i = 0; i < 3; i++) {
      refundService.processPending();
    }
    performAdmin(get("/api/admin/orders?refundStatus=FAILED&keyword=" + order.id))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].refundStatus").value("FAILED"));
    perform(account, get("/api/orders/%d".formatted(order.id)))
        .andExpect(jsonPath("$.refund.status").value("FAILED"));

    performAdmin(put("/api/admin/orders/%d/refund/complete".formatted(order.id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.refund.status").value("COMPLETED"));
    performAdmin(put("/api/admin/orders/%d/refund/complete".formatted(order.id)))
        .andExpect(status().isConflict());

    // Lần khác VNPay nhận hoàn tiền ngay: chuyển Đã hoàn tiền.
    Order second = placeOnline(account, variant, 1, 130_000);
    ipn(callback(queryOf(paymentUrl(account, second.id)), "00", "00"));
    performAdmin(changeStatus(second.id, "CANCELLED")).andExpect(status().isOk());
    doReturn(RefundResult.success()).when(vnPayClient).refund(any());
    refundService.processPending();
    perform(account, get("/api/orders/%d".formatted(second.id)))
        .andExpect(jsonPath("$.refund.status").value("COMPLETED"))
        .andExpect(jsonPath("$.refund.completedAt").exists());
  }

  @Test
  void unpaidOnlineOrderIsCancelledAfterThirtyMinutesAndLatePaymentIsRefunded() throws Exception {
    long account = account();
    long variant = variantOf(product(100_000L, 5));
    Order fresh = placeOnline(account, variant, 1, 130_000);
    Order expired = placeOnline(account, variant, 2, 230_000);
    Map<String, String> late = callback(queryOf(paymentUrl(account, expired.id)), "00", "00");
    jdbcTemplate.update(
        "UPDATE orders SET created_at = created_at - INTERVAL 31 MINUTE WHERE id = ?", expired.id);
    assertThat(stockOf(variant)).isEqualTo(2);

    assertThat(paymentService.cancelExpiredOnlineOrders()).isEqualTo(1);
    perform(account, get("/api/orders/%d".formatted(expired.id)))
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.paymentStatus").value("FAILED"))
        .andExpect(jsonPath("$.refund").doesNotExist());
    perform(account, get("/api/orders/%d".formatted(fresh.id)))
        .andExpect(jsonPath("$.status").value("PENDING"));
    assertThat(stockOf(variant)).isEqualTo(4);
    assertThat(paymentService.cancelExpiredOnlineOrders()).isZero();
    perform(account, post("/api/orders/%d/payment-url".formatted(expired.id)))
        .andExpect(status().isConflict());

    // Khách trả tiền sau khi đơn đã tự hủy: ghi nhận và hoàn tiền (BR-28).
    mockMvc
        .perform(get(URI.create("/api/payments/vnpay/return" + query(late))))
        .andExpect(jsonPath("$.paid").value(false))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("hoàn")));
    perform(account, get("/api/orders/%d".formatted(expired.id)))
        .andExpect(jsonPath("$.paymentStatus").value("PAID"))
        .andExpect(jsonPath("$.refund.status").value("PROCESSING"));

    // Khách tự hủy đơn chưa thanh toán: thanh toán chuyển FAILED, không hoàn tiền.
    perform(account, post("/api/orders/%d/cancel".formatted(fresh.id)))
        .andExpect(jsonPath("$.paymentStatus").value("FAILED"))
        .andExpect(jsonPath("$.refund").doesNotExist());
    assertThat(stockOf(variant)).isEqualTo(5);
  }

  // ---------------------------------------------------------------- tiện ích

  private record Order(long id, long total) {}

  private Order placeOnline(long account, long variant, int quantity, long total) throws Exception {
    perform(
            account,
            post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\": %d, \"quantity\": %d}".formatted(variant, quantity)))
        .andExpect(status().isOk());
    String address =
        perform(
                account,
                post("/api/addresses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"recipientName\": \"A\", \"phone\": \"0901234567\","
                            + " \"addressDetail\": \"TP HCM\", \"isDefault\": true}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long addressId = ((Number) JsonPath.read(address, "$.id")).longValue();
    String body =
        perform(
                account,
                post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        ("{\"idempotencyKey\": \"%s\", \"paymentMethod\": \"ONLINE\","
                                + " \"addressId\": %d, \"expectedTotal\": %d}")
                            .formatted(UUID.randomUUID(), addressId, total)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Order(((Number) JsonPath.read(body, "$.id")).longValue(), total);
  }

  private String paymentUrl(long account, long orderId) throws Exception {
    String body =
        perform(account, post("/api/orders/%d/payment-url".formatted(orderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.paymentUrl");
  }

  /** Dữ liệu VNPay gửi về cho giao dịch này, đã ký bằng khóa bí mật. */
  private static Map<String, String> callback(
      Map<String, String> sent, String responseCode, String transactionStatus) {
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_Amount", sent.get("vnp_Amount"));
    params.put("vnp_BankCode", "NCB");
    params.put("vnp_OrderInfo", sent.get("vnp_OrderInfo"));
    params.put("vnp_PayDate", "20261021120500");
    params.put("vnp_ResponseCode", responseCode);
    params.put("vnp_TmnCode", sent.get("vnp_TmnCode"));
    params.put("vnp_TransactionNo", "14000001");
    params.put("vnp_TransactionStatus", transactionStatus);
    params.put("vnp_TxnRef", sent.get("vnp_TxnRef"));
    return signed(params);
  }

  private static Map<String, String> signed(Map<String, String> params) {
    Map<String, String> result = new TreeMap<>(params);
    result.remove("vnp_SecureHash");
    result.remove("vnp_SecureHashType");
    result.put("vnp_SecureHash", hmac(encode(result)));
    result.put("vnp_SecureHashType", "HmacSHA512");
    return result;
  }

  private static String encode(Map<String, String> params) {
    return new TreeMap<>(params)
        .entrySet().stream()
            .filter(e -> !e.getKey().equals("vnp_SecureHash"))
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
            .collect(Collectors.joining("&"));
  }

  private static String hmac(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA512");
      mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static Map<String, String> queryOf(String url) {
    Map<String, String> params = new LinkedHashMap<>();
    for (String pair : URI.create(url).getRawQuery().split("&")) {
      String[] kv = pair.split("=", 2);
      params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
    }
    return params;
  }

  private static String query(Map<String, String> params) {
    return "?"
        + params.entrySet().stream()
            .map(
                e ->
                    e.getKey()
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)
                            .replace("+", "%20"))
            .collect(Collectors.joining("&"));
  }

  private ResultActions ipn(Map<String, String> params) throws Exception {
    return mockMvc
        .perform(get(URI.create("/api/payments/vnpay/ipn" + query(params))))
        .andExpect(status().isOk());
  }

  private static MockHttpServletRequestBuilder changeStatus(long orderId, String status) {
    return put("/api/admin/orders/%d/status".formatted(orderId))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\": \"%s\"}".formatted(status));
  }

  private ResultActions perform(long accountId, MockHttpServletRequestBuilder request)
      throws Exception {
    return mockMvc.perform(
        request.with(
            jwt()
                .jwt(j -> j.subject(String.valueOf(accountId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

  private ResultActions performAdmin(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.with(
            jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private long account() {
    Account account =
        accountRepository.save(
            new Account(
                "thanh-toan-" + UUID.randomUUID() + "@test.local",
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
                "Sản phẩm thanh toán " + UUID.randomUUID(),
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
