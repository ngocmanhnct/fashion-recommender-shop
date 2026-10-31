package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.MyVoucherResponse;
import com.manhdn.fashionshop.service.VoucherService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API mã giảm giá của khách hàng (US-26, trang "Ưu đãi của bạn"). */
@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {

  private final VoucherService voucherService;

  /** Nhận VoucherService qua constructor. */
  public VoucherController(VoucherService voucherService) {
    this.voucherService = voucherService;
  }

  /**
   * Mã riêng khách được tặng và còn dùng được, hết hạn sớm nhất trước.
   *
   * @param jwt token của khách
   * @return 200 kèm danh sách mã (có thể rỗng)
   */
  @GetMapping("/me")
  public List<MyVoucherResponse> mine(@AuthenticationPrincipal Jwt jwt) {
    return voucherService.myVouchers(Long.parseLong(jwt.getSubject()));
  }
}
