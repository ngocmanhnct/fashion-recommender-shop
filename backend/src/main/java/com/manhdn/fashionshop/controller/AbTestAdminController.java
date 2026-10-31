package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.AbTestRequest;
import com.manhdn.fashionshop.dto.AbTestResponse;
import com.manhdn.fashionshop.dto.AbTestResultResponse;
import com.manhdn.fashionshop.service.AbTestService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API thử nghiệm A/B gợi ý ở trang chủ cho Quản trị viên (US-27; chỉ ADMIN, SecurityConfig). */
@RestController
@RequestMapping("/api/admin/ab-tests")
public class AbTestAdminController {

  private final AbTestService abTestService;

  /** Nhận service qua constructor. */
  public AbTestAdminController(AbTestService abTestService) {
    this.abTestService = abTestService;
  }

  /**
   * Danh sách thử nghiệm, mới nhất trước.
   *
   * @return các thử nghiệm
   */
  @GetMapping
  public List<AbTestResponse> list() {
    return abTestService.list();
  }

  /**
   * Bắt đầu thử nghiệm mới; 409 nếu đang có thử nghiệm chạy.
   *
   * @param request tên và tỷ lệ khách vào nhóm gợi ý (10-90%)
   * @return thử nghiệm vừa tạo
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AbTestResponse start(@Valid @RequestBody AbTestRequest request) {
    return abTestService.start(request);
  }

  /**
   * Kết quả của một thử nghiệm: số liệu 2 nhóm và kiểm định z hai tỷ lệ (BR-46).
   *
   * @param id mã thử nghiệm
   * @return kết quả
   */
  @GetMapping("/{id}")
  public AbTestResultResponse result(@PathVariable long id) {
    return abTestService.result(id);
  }

  /**
   * Dừng thử nghiệm; 400 nếu đã dừng.
   *
   * @param id mã thử nghiệm
   * @return thử nghiệm sau khi dừng
   */
  @PostMapping("/{id}/stop")
  public AbTestResponse stop(@PathVariable long id) {
    return abTestService.stop(id);
  }
}
