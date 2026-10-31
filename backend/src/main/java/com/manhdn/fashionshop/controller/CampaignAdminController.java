package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.CampaignRequest;
import com.manhdn.fashionshop.dto.CampaignResponse;
import com.manhdn.fashionshop.service.CampaignService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API chiến dịch ưu đãi theo nhóm khách hàng của Quản trị viên (US-26). */
@RestController
@RequestMapping("/api/admin")
public class CampaignAdminController {

  private final CampaignService campaignService;

  /** Nhận CampaignService qua constructor. */
  public CampaignAdminController(CampaignService campaignService) {
    this.campaignService = campaignService;
  }

  /**
   * Tặng mã giảm giá riêng cho cả nhóm hoặc khách cần chăm sóc của nhóm.
   *
   * @param code mã nhóm, vd AT_RISK
   * @param request đối tượng (ALL, CARE) và điều kiện của mã
   * @return 201 kèm chiến dịch; 404 nếu không có nhóm; 409 nếu không có khách để tặng hoặc trùng
   *     mã; 400 nếu điều kiện mã sai
   */
  @PostMapping("/segments/{code}/campaigns")
  @ResponseStatus(HttpStatus.CREATED)
  public CampaignResponse create(
      @PathVariable String code, @Valid @RequestBody CampaignRequest request) {
    return campaignService.create(code, request);
  }

  /**
   * Các chiến dịch kèm kết quả (khách đã quay lại mua, doanh thu, tiền đã giảm), mới nhất trước.
   *
   * @param segment mã nhóm để lọc; bỏ trống là mọi nhóm
   * @return 200 kèm tối đa 50 chiến dịch
   */
  @GetMapping("/campaigns")
  public List<CampaignResponse> list(@RequestParam(required = false) String segment) {
    return campaignService.list(segment);
  }
}
