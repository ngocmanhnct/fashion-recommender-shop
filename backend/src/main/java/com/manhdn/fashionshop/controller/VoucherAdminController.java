package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.VoucherActiveRequest;
import com.manhdn.fashionshop.dto.VoucherRequest;
import com.manhdn.fashionshop.dto.VoucherResponse;
import com.manhdn.fashionshop.service.VoucherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API quản lý mã giảm giá (US-22), chỉ cho Quản trị viên (đường dẫn /api/admin). */
@RestController
@RequestMapping("/api/admin/vouchers")
public class VoucherAdminController {

  private final VoucherService voucherService;

  /** Nhận VoucherService qua constructor. */
  public VoucherAdminController(VoucherService voucherService) {
    this.voucherService = voucherService;
  }

  /**
   * Danh sách mã, mới tạo trước, kèm số lượt đã dùng, còn lại và tình trạng.
   *
   * @param keyword một phần mã (không bắt buộc)
   * @param page số trang, từ 0
   * @param size số mã mỗi trang, tối đa 50
   * @return một trang mã giảm giá
   */
  @GetMapping
  public PageResponse<VoucherResponse> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return voucherService.list(keyword, page, size);
  }

  /**
   * Tạo mã giảm giá.
   *
   * @param request điều kiện của mã
   * @return 201 kèm mã vừa tạo; 400 nếu điều kiện sai; 409 nếu trùng mã
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public VoucherResponse create(@Valid @RequestBody VoucherRequest request) {
    return voucherService.create(request);
  }

  /**
   * Sửa mã giảm giá.
   *
   * @param id mã số
   * @param request điều kiện mới
   * @return mã sau khi sửa; 404 nếu không có; 409 nếu trùng mã hoặc đổi chuỗi mã đã có người dùng
   */
  @PutMapping("/{id}")
  public VoucherResponse update(@PathVariable long id, @Valid @RequestBody VoucherRequest request) {
    return voucherService.update(id, request);
  }

  /**
   * Bật lại hoặc ngừng áp dụng mã trước hạn.
   *
   * @param id mã số
   * @param request trạng thái mới
   * @return mã sau khi đổi; 404 nếu không có
   */
  @PutMapping("/{id}/active")
  public VoucherResponse setActive(
      @PathVariable long id, @Valid @RequestBody VoucherActiveRequest request) {
    return voucherService.setActive(id, request.active());
  }
}
