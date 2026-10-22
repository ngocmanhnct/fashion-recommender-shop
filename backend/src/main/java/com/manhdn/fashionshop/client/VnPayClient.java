package com.manhdn.fashionshop.client;

import com.manhdn.fashionshop.config.VnPayProperties;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gọi cổng thanh toán VNPay bản 2.1.0: tạo đường dẫn thanh toán, kiểm tra chữ ký dữ liệu VNPay gửi
 * về (trang trả về và IPN), gọi API hoàn tiền (BR-28). Mọi chữ ký dùng HMAC-SHA512 với khóa bí mật.
 */
@Component
public class VnPayClient {

  /** Định dạng thời gian VNPay dùng (giờ Việt Nam, GMT+7). */
  public static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private static final String VERSION = "2.1.0";
  private static final String SECURE_HASH = "vnp_SecureHash";
  private static final String HMAC_SHA512 = "HmacSHA512";

  private final VnPayProperties properties;
  private final RestClient restClient;

  /** Nhận cấu hình VNPay; API hoàn tiền gọi với thời gian chờ 10 giây. */
  public VnPayClient(VnPayProperties properties) {
    this.properties = properties;
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofSeconds(10));
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /** Đã cấu hình mã website và khóa bí mật chưa. */
  public boolean configured() {
    return properties.configured();
  }

  /**
   * Đường dẫn chuyển khách sang trang thanh toán của VNPay.
   *
   * @param request thông tin giao dịch
   * @return URL đã ký
   */
  public String paymentUrl(PaymentRequest request) {
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_Version", VERSION);
    params.put("vnp_Command", "pay");
    params.put("vnp_TmnCode", properties.tmnCode());
    params.put("vnp_Amount", String.valueOf(request.amount() * 100));
    params.put("vnp_CurrCode", "VND");
    params.put("vnp_TxnRef", request.txnRef());
    params.put("vnp_OrderInfo", request.orderInfo());
    params.put("vnp_OrderType", "other");
    params.put("vnp_Locale", "vn");
    params.put("vnp_ReturnUrl", properties.returnUrl());
    params.put("vnp_IpAddr", request.ipAddress());
    params.put("vnp_CreateDate", VNPAY_TIME.format(request.createdAt()));
    params.put("vnp_ExpireDate", VNPAY_TIME.format(request.expiresAt()));
    String query = encode(params);
    return properties.payUrl() + "?" + query + "&" + SECURE_HASH + "=" + hmac(query);
  }

  /**
   * Kiểm tra chữ ký của dữ liệu VNPay gửi về (trang trả về hoặc IPN): ký lại mọi tham số vnp_ trừ
   * vnp_SecureHash, vnp_SecureHashType, sắp theo tên, rồi so với vnp_SecureHash.
   *
   * @param params toàn bộ tham số trên URL
   * @return true nếu đúng chữ ký của VNPay
   */
  public boolean verify(Map<String, String> params) {
    String received = params.get(SECURE_HASH);
    if (received == null || !configured()) {
      return false;
    }
    Map<String, String> signed = new TreeMap<>();
    params.forEach(
        (key, value) -> {
          if (key.startsWith("vnp_")
              && !key.equals(SECURE_HASH)
              && !key.equals("vnp_SecureHashType")
              && value != null
              && !value.isEmpty()) {
            signed.put(key, value);
          }
        });
    return constantTimeEquals(hmac(encode(signed)), received.toLowerCase());
  }

  /**
   * Gọi API hoàn tiền toàn phần của VNPay cho một giao dịch đã thanh toán.
   *
   * @param request thông tin giao dịch cần hoàn
   * @return kết quả: thành công, hoặc lý do thất bại
   */
  public RefundResult refund(RefundRequest request) {
    if (!configured()) {
      return RefundResult.failed("Cửa hàng chưa cấu hình VNPay");
    }
    String requestId = UUID.randomUUID().toString().replace("-", "");
    String createDate = VNPAY_TIME.format(request.createdAt());
    String amount = String.valueOf(request.amount() * 100);
    Map<String, String> body = new LinkedHashMap<>();
    body.put("vnp_RequestId", requestId);
    body.put("vnp_Version", VERSION);
    body.put("vnp_Command", "refund");
    body.put("vnp_TmnCode", properties.tmnCode());
    body.put("vnp_TransactionType", "02");
    body.put("vnp_TxnRef", request.txnRef());
    body.put("vnp_Amount", amount);
    body.put("vnp_TransactionNo", request.transactionNo());
    body.put("vnp_TransactionDate", request.transactionDate());
    body.put("vnp_CreateBy", "fashionshop");
    body.put("vnp_CreateDate", createDate);
    body.put("vnp_IpAddr", request.ipAddress());
    body.put("vnp_OrderInfo", request.orderInfo());
    // Thứ tự các trường ký của API hoàn tiền do VNPay quy định, không sắp theo tên.
    String data =
        String.join(
            "|",
            requestId,
            VERSION,
            "refund",
            properties.tmnCode(),
            "02",
            request.txnRef(),
            amount,
            request.transactionNo(),
            request.transactionDate(),
            "fashionshop",
            createDate,
            request.ipAddress(),
            request.orderInfo());
    body.put(SECURE_HASH, hmac(data));

    Map<String, Object> response;
    try {
      response =
          restClient
              .post()
              .uri(properties.apiUrl())
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    } catch (RestClientException ex) {
      return RefundResult.failed("Không gọi được VNPay: " + ex.getMessage());
    }
    if (response == null) {
      return RefundResult.failed("VNPay không trả về dữ liệu");
    }
    String code = text(response, "vnp_ResponseCode");
    if (!"00".equals(code)) {
      return RefundResult.failed(
          "VNPay từ chối hoàn tiền (mã %s): %s".formatted(code, text(response, "vnp_Message")));
    }
    if (!verifyRefundResponse(response)) {
      return RefundResult.failed("Chữ ký phản hồi hoàn tiền của VNPay không hợp lệ");
    }
    return RefundResult.success();
  }

  private boolean verifyRefundResponse(Map<String, Object> response) {
    String data =
        String.join(
            "|",
            text(response, "vnp_ResponseId"),
            text(response, "vnp_Command"),
            text(response, "vnp_ResponseCode"),
            text(response, "vnp_Message"),
            text(response, "vnp_TmnCode"),
            text(response, "vnp_TxnRef"),
            text(response, "vnp_Amount"),
            text(response, "vnp_BankCode"),
            text(response, "vnp_PayDate"),
            text(response, "vnp_TransactionNo"),
            text(response, "vnp_TransactionType"),
            text(response, "vnp_TransactionStatus"),
            text(response, "vnp_OrderInfo"));
    return constantTimeEquals(hmac(data), text(response, SECURE_HASH).toLowerCase());
  }

  private static String text(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value == null ? "" : value.toString();
  }

  /** Nối các cặp tên=giá trị đã mã hóa URL (US-ASCII, dấu cách thành +), theo thứ tự của map. */
  private static String encode(Map<String, String> params) {
    return params.entrySet().stream()
        .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.US_ASCII);
  }

  private String hmac(String data) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA512);
      mac.init(
          new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("Không ký được dữ liệu VNPay", ex);
    }
  }

  private static boolean constantTimeEquals(String expected, String actual) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
  }

  /**
   * Thông tin để tạo đường dẫn thanh toán.
   *
   * @param txnRef mã tham chiếu giao dịch, duy nhất mỗi lần tạo
   * @param amount số tiền VNĐ
   * @param orderInfo mô tả đơn, không dấu
   * @param ipAddress địa chỉ IP của khách
   * @param createdAt thời điểm tạo, giờ Việt Nam
   * @param expiresAt thời điểm đường dẫn hết hiệu lực, giờ Việt Nam
   */
  public record PaymentRequest(
      String txnRef,
      long amount,
      String orderInfo,
      String ipAddress,
      ZonedDateTime createdAt,
      ZonedDateTime expiresAt) {}

  /**
   * Thông tin để gọi hoàn tiền.
   *
   * @param txnRef mã tham chiếu của giao dịch đã thanh toán
   * @param transactionNo mã giao dịch phía VNPay
   * @param transactionDate thời điểm tạo giao dịch thanh toán, dạng yyyyMMddHHmmss
   * @param amount số tiền hoàn, VNĐ
   * @param orderInfo mô tả, không dấu
   * @param ipAddress địa chỉ IP máy chủ gọi
   * @param createdAt thời điểm gọi, giờ Việt Nam
   */
  public record RefundRequest(
      String txnRef,
      String transactionNo,
      String transactionDate,
      long amount,
      String orderInfo,
      String ipAddress,
      ZonedDateTime createdAt) {}

  /**
   * Kết quả gọi hoàn tiền.
   *
   * @param succeeded VNPay đã nhận hoàn tiền
   * @param failureReason lý do thất bại, null nếu thành công
   */
  public record RefundResult(boolean succeeded, String failureReason) {

    /** Thành công. */
    public static RefundResult success() {
      return new RefundResult(true, null);
    }

    /** Thất bại kèm lý do. */
    public static RefundResult failed(String reason) {
      return new RefundResult(false, reason);
    }
  }
}
