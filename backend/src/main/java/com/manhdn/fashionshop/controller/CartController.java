package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.CartItemRequest;
import com.manhdn.fashionshop.dto.CartLinesRequest;
import com.manhdn.fashionshop.dto.CartQuantityRequest;
import com.manhdn.fashionshop.dto.CartResponse;
import com.manhdn.fashionshop.service.CartService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API giỏ hàng (US-06, US-07, BR-06, BR-07, BR-11). Mọi API trả về toàn bộ giỏ hàng sau thao tác,
 * kèm thông báo về các dòng hệ thống vừa tự loại hoặc giảm số lượng.
 *
 * <p>{@code POST /api/cart/preview} công khai, dành cho giỏ hàng lưu ở trình duyệt của khách chưa
 * đăng nhập; các API còn lại chỉ cho Khách hàng (không token 401, Quản trị viên 403).
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

  private final CartService cartService;

  /** Nhận service qua constructor. */
  public CartController(CartService cartService) {
    this.cartService = cartService;
  }

  /**
   * Giỏ hàng của Khách hàng đang đăng nhập theo giá và tồn kho hiện tại.
   *
   * @param jwt token của Khách hàng, claim sub là mã tài khoản
   * @return 200 kèm giỏ hàng
   */
  @GetMapping
  public CartResponse get(@AuthenticationPrincipal Jwt jwt) {
    return cartService.getCart(accountId(jwt));
  }

  /**
   * Thêm biến thể vào giỏ hàng, cộng dồn nếu đã có.
   *
   * @param request biến thể và số lượng (1 đến 99)
   * @param jwt token của Khách hàng
   * @return 200 kèm giỏ hàng; 400 nếu dữ liệu sai; 404 nếu biến thể không có hoặc sản phẩm đã ngừng
   *     bán; 409 nếu hết hàng, vượt tồn kho, vượt 99 hoặc giỏ đã đủ 50 dòng
   */
  @PostMapping("/items")
  public CartResponse add(
      @Valid @RequestBody CartItemRequest request, @AuthenticationPrincipal Jwt jwt) {
    return cartService.addItem(accountId(jwt), request);
  }

  /**
   * Đổi số lượng một dòng giỏ hàng.
   *
   * @param variantId mã biến thể của dòng
   * @param request số lượng mới (1 đến 99)
   * @param jwt token của Khách hàng
   * @return 200 kèm giỏ hàng; 400 nếu dữ liệu sai; 404 nếu dòng không có trong giỏ; 409 nếu vượt
   *     tồn kho
   */
  @PutMapping("/items/{variantId}")
  public CartResponse update(
      @PathVariable Long variantId,
      @Valid @RequestBody CartQuantityRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return cartService.updateQuantity(accountId(jwt), variantId, request.quantity());
  }

  /**
   * Xóa một dòng khỏi giỏ hàng; dòng không có thì không làm gì.
   *
   * @param variantId mã biến thể của dòng
   * @param jwt token của Khách hàng
   * @return 200 kèm giỏ hàng
   */
  @DeleteMapping("/items/{variantId}")
  public CartResponse remove(@PathVariable Long variantId, @AuthenticationPrincipal Jwt jwt) {
    return cartService.removeItem(accountId(jwt), variantId);
  }

  /**
   * Gộp giỏ hàng của trình duyệt vào giỏ của tài khoản ngay sau khi đăng nhập hoặc đăng ký (BR-07).
   *
   * @param request các dòng giỏ hàng của trình duyệt (tối đa 50)
   * @param jwt token của Khách hàng
   * @return 200 kèm giỏ hàng sau khi gộp; 400 nếu dữ liệu sai
   */
  @PostMapping("/merge")
  public CartResponse merge(
      @Valid @RequestBody CartLinesRequest request, @AuthenticationPrincipal Jwt jwt) {
    return cartService.merge(accountId(jwt), request.items());
  }

  /**
   * Xem trước giỏ hàng lưu ở trình duyệt của khách chưa đăng nhập theo giá và tồn kho hiện tại,
   * không lưu gì (BR-06). Công khai.
   *
   * @param request các dòng giỏ hàng của trình duyệt (tối đa 50)
   * @return 200 kèm giỏ hàng đã kiểm tra; 400 nếu dữ liệu sai
   */
  @PostMapping("/preview")
  public CartResponse preview(@Valid @RequestBody CartLinesRequest request) {
    return cartService.preview(request.items());
  }

  private static long accountId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
