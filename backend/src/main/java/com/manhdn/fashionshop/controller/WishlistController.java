package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.WishlistItemResponse;
import com.manhdn.fashionshop.service.WishlistService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API danh sách yêu thích (US-14), chỉ cho Khách hàng (không token 401, Quản trị viên 403). Thêm và
 * bỏ dùng PUT/DELETE trên mã sản phẩm, gọi lại nhiều lần vẫn cho cùng kết quả.
 */
@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

  private final WishlistService wishlistService;

  /** Nhận service qua constructor. */
  public WishlistController(WishlistService wishlistService) {
    this.wishlistService = wishlistService;
  }

  /**
   * Các sản phẩm còn bán trong danh sách yêu thích, thêm sau đứng trước.
   *
   * @param jwt token của Khách hàng, claim sub là mã tài khoản
   * @return 200 kèm danh sách thẻ sản phẩm và lúc thêm
   */
  @GetMapping
  public List<WishlistItemResponse> list(@AuthenticationPrincipal Jwt jwt) {
    return wishlistService.list(accountId(jwt));
  }

  /**
   * Mã các sản phẩm trong danh sách yêu thích, để tô nút yêu thích ở trang sản phẩm.
   *
   * @param jwt token của Khách hàng
   * @return 200 kèm mã sản phẩm tăng dần
   */
  @GetMapping("/ids")
  public List<Long> productIds(@AuthenticationPrincipal Jwt jwt) {
    return wishlistService.productIds(accountId(jwt));
  }

  /**
   * Thêm sản phẩm vào danh sách yêu thích; đã có thì không làm gì.
   *
   * @param productId mã sản phẩm
   * @param jwt token của Khách hàng
   * @return 200 kèm mã các sản phẩm trong danh sách; 404 nếu sản phẩm không có hoặc đã ngừng bán;
   *     409 nếu danh sách đã đủ 100 sản phẩm
   */
  @PutMapping("/{productId}")
  public List<Long> add(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt) {
    return wishlistService.add(accountId(jwt), productId);
  }

  /**
   * Bỏ sản phẩm khỏi danh sách yêu thích; không có thì không làm gì.
   *
   * @param productId mã sản phẩm
   * @param jwt token của Khách hàng
   * @return 200 kèm mã các sản phẩm còn lại trong danh sách
   */
  @DeleteMapping("/{productId}")
  public List<Long> remove(@PathVariable Long productId, @AuthenticationPrincipal Jwt jwt) {
    return wishlistService.remove(accountId(jwt), productId);
  }

  private static long accountId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
