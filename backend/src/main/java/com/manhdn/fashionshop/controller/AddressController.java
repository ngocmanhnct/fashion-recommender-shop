package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.AddressRequest;
import com.manhdn.fashionshop.dto.AddressResponse;
import com.manhdn.fashionshop.service.AddressService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API sổ địa chỉ giao hàng (US-09), chỉ cho Khách hàng (không token 401, Quản trị viên 403). Mỗi
 * khách chỉ thấy và sửa được địa chỉ của chính mình; địa chỉ của người khác trả 404.
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

  private final AddressService addressService;

  /** Nhận service qua constructor. */
  public AddressController(AddressService addressService) {
    this.addressService = addressService;
  }

  /**
   * Sổ địa chỉ của Khách hàng đang đăng nhập, địa chỉ mặc định đứng đầu.
   *
   * @param jwt token của Khách hàng, claim sub là mã tài khoản
   * @return 200 kèm danh sách địa chỉ
   */
  @GetMapping
  public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
    return addressService.list(accountId(jwt));
  }

  /**
   * Thêm địa chỉ. Địa chỉ đầu tiên tự là mặc định.
   *
   * @param request người nhận, số điện thoại, địa chỉ, có đặt làm mặc định không
   * @param jwt token của Khách hàng
   * @return 201 kèm địa chỉ vừa thêm; 400 nếu dữ liệu sai; 409 nếu sổ đã đủ 10 địa chỉ
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AddressResponse create(
      @Valid @RequestBody AddressRequest request, @AuthenticationPrincipal Jwt jwt) {
    return addressService.create(accountId(jwt), request);
  }

  /**
   * Sửa địa chỉ; đơn đã đặt không đổi theo.
   *
   * @param id mã địa chỉ
   * @param request thông tin mới
   * @param jwt token của Khách hàng
   * @return 200 kèm địa chỉ sau khi sửa; 400 nếu dữ liệu sai; 404 nếu không có địa chỉ này
   */
  @PutMapping("/{id}")
  public AddressResponse update(
      @PathVariable long id,
      @Valid @RequestBody AddressRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return addressService.update(accountId(jwt), id, request);
  }

  /**
   * Xóa địa chỉ; đơn đã đặt vẫn giữ bản sao địa chỉ.
   *
   * @param id mã địa chỉ
   * @param jwt token của Khách hàng
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
    addressService.delete(accountId(jwt), id);
  }

  private static long accountId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
