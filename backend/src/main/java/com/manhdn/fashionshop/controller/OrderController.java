package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.CheckoutPreviewResponse;
import com.manhdn.fashionshop.dto.OrderResponse;
import com.manhdn.fashionshop.dto.OrderSummaryResponse;
import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.PaymentUrlResponse;
import com.manhdn.fashionshop.dto.PlaceOrderRequest;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.service.OrderService;
import com.manhdn.fashionshop.service.OrderService.PlaceOrderResult;
import com.manhdn.fashionshop.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API đặt hàng và đơn hàng của Khách hàng (US-10, US-11, US-20), chỉ cho Khách hàng (không token
 * 401, Quản trị viên 403). Đơn của người khác trả 404.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderService orderService;
  private final PaymentService paymentService;

  /** Nhận service qua constructor. */
  public OrderController(OrderService orderService, PaymentService paymentService) {
    this.orderService = orderService;
    this.paymentService = paymentService;
  }

  /**
   * Tóm tắt đơn hàng trước khi xác nhận: giỏ hàng theo giá hiện tại, phí vận chuyển, tổng tiền.
   *
   * @param voucherCode mã giảm giá khách nhập (không bắt buộc); không dùng được thì không giảm và
   *     trả lý do ở voucherError
   * @param jwt token của Khách hàng, claim sub là mã tài khoản
   * @return 200 kèm tóm tắt; giỏ đã tự loại dòng không còn mua được, kèm thông báo
   */
  @GetMapping("/preview")
  public CheckoutPreviewResponse preview(
      @RequestParam(required = false) String voucherCode, @AuthenticationPrincipal Jwt jwt) {
    if (voucherCode != null && voucherCode.length() > 50) {
      throw new InvalidRequestException("Mã giảm giá tối đa 50 ký tự");
    }
    return orderService.preview(accountId(jwt), voucherCode);
  }

  /**
   * Đặt toàn bộ giỏ hàng thành một đơn (thanh toán khi nhận hàng).
   *
   * @param request mã định danh lần bấm, phương thức thanh toán, địa chỉ, tổng tiền đã xem
   * @param jwt token của Khách hàng
   * @return 201 kèm đơn vừa tạo; 200 kèm đơn đã tạo nếu gửi lại cùng mã định danh; 400 nếu dữ liệu
   *     sai hoặc chọn thanh toán online; 404 nếu không có địa chỉ; 409 nếu giỏ rỗng, có sản phẩm
   *     ngừng bán hoặc không đủ hàng, mã giảm giá không còn dùng được, hay tổng tiền đã đổi
   */
  @PostMapping
  public ResponseEntity<OrderResponse> place(
      @Valid @RequestBody PlaceOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
    PlaceOrderResult result = orderService.placeOrder(accountId(jwt), request);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result.order());
  }

  /**
   * Đơn hàng của Khách hàng, mới nhất trước.
   *
   * @param page số trang, bắt đầu từ 0
   * @param size số đơn mỗi trang, 1 đến 50
   * @param jwt token của Khách hàng
   * @return 200 kèm một trang đơn hàng; 400 nếu tham số trang sai
   */
  @GetMapping
  public PageResponse<OrderSummaryResponse> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @AuthenticationPrincipal Jwt jwt) {
    return orderService.list(accountId(jwt), page, size);
  }

  /**
   * Chi tiết một đơn hàng.
   *
   * @param id mã đơn hàng
   * @param jwt token của Khách hàng
   * @return 200 kèm chi tiết đơn; 404 nếu không có đơn này
   */
  @GetMapping("/{id}")
  public OrderResponse get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
    return orderService.get(accountId(jwt), id);
  }

  /**
   * Khách tự hủy đơn khi đơn còn Chờ xử lý hoặc Đang xử lý; tồn kho được hoàn lại.
   *
   * @param id mã đơn hàng
   * @param jwt token của Khách hàng
   * @return 200 kèm đơn sau khi hủy; 404 nếu không có đơn này; 409 nếu đơn đã rời kho
   */
  @PostMapping("/{id}/cancel")
  public OrderResponse cancel(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
    return orderService.cancel(accountId(jwt), id);
  }

  /**
   * Tạo đường dẫn thanh toán VNPay cho đơn thanh toán online còn chờ thanh toán (lần đầu hoặc thanh
   * toán lại).
   *
   * @param id mã đơn hàng
   * @param jwt token của Khách hàng
   * @param request request HTTP, để lấy địa chỉ IP của khách gửi cho VNPay
   * @return 200 kèm đường dẫn; 404 nếu không có đơn; 409 nếu đơn không còn chờ thanh toán; 400 nếu
   *     cửa hàng chưa bật VNPay
   */
  @PostMapping("/{id}/payment-url")
  public PaymentUrlResponse paymentUrl(
      @PathVariable long id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
    return paymentService.createPaymentUrl(accountId(jwt), id, clientIp(request));
  }

  /** Địa chỉ IPv4 của khách; địa chỉ loopback IPv6 đổi thành 127.0.0.1 cho VNPay. */
  private static String clientIp(HttpServletRequest request) {
    String ip = request.getRemoteAddr();
    return ip == null || ip.contains(":") ? "127.0.0.1" : ip;
  }

  private static long accountId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
