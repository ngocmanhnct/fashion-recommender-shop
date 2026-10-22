package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.BrandResponse;
import com.manhdn.fashionshop.dto.MySurveyResponse;
import com.manhdn.fashionshop.dto.SurveyAnswersRequest;
import com.manhdn.fashionshop.dto.SurveyEvaluationResponse;
import com.manhdn.fashionshop.dto.SurveyResponse;
import com.manhdn.fashionshop.service.SurveyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API khảo sát lần đầu dạng rẽ nhánh (US-23, BR-37, BR-38, BR-41, BR-42).
 *
 * <p>{@code GET /api/survey}, {@code POST /api/survey/brands}, {@code POST /api/survey/evaluate}
 * công khai: khách chưa đăng nhập làm khảo sát và xem gợi ý, câu trả lời lưu ở trình duyệt. Các API
 * {@code /api/survey/me} chỉ cho Khách hàng (không token 401, Quản trị viên 403).
 */
@RestController
@RequestMapping("/api/survey")
public class SurveyController {

  private final SurveyService surveyService;

  /** Nhận service qua constructor. */
  public SurveyController(SurveyService surveyService) {
    this.surveyService = surveyService;
  }

  /**
   * Toàn bộ cây câu hỏi.
   *
   * @return 200 kèm câu hỏi đầu tiên và mọi câu hỏi, đáp án, câu tiếp theo của từng đáp án
   */
  @GetMapping
  public SurveyResponse get() {
    return surveyService.getSurvey();
  }

  /**
   * Thương hiệu cho câu chọn thương hiệu, theo các danh mục suy ra từ những câu đã trả lời.
   *
   * @param request các câu trả lời từ câu đầu tiên đến ngay trước câu chọn thương hiệu
   * @return 200 kèm tối đa 15 thương hiệu; 400 nếu câu trả lời sai hoặc chưa tới câu chọn thương
   *     hiệu
   */
  @PostMapping("/brands")
  public List<BrandResponse> brands(@Valid @RequestBody SurveyAnswersRequest request) {
    return surveyService.brandChoices(request.answers());
  }

  /**
   * Kết quả khảo sát và sản phẩm gợi ý, không lưu gì.
   *
   * @param request câu trả lời của một lần làm hết khảo sát (cả nhánh tìm quà)
   * @return 200 kèm hồ sơ mua sắm và tối đa 8 sản phẩm kèm lý do; 400 nếu câu trả lời sai hoặc chưa
   *     trả lời hết
   */
  @PostMapping("/evaluate")
  public SurveyEvaluationResponse evaluate(@Valid @RequestBody SurveyAnswersRequest request) {
    return surveyService.evaluate(request.answers());
  }

  /**
   * Khảo sát của Khách hàng đang đăng nhập.
   *
   * @param jwt token của Khách hàng, claim sub là mã tài khoản
   * @return 200 kèm trạng thái NONE, COMPLETED hoặc SKIPPED, câu trả lời và hồ sơ của lần làm gần
   *     nhất
   */
  @GetMapping("/me")
  public MySurveyResponse getMine(@AuthenticationPrincipal Jwt jwt) {
    return surveyService.getMine(accountId(jwt));
  }

  /**
   * Lưu kết quả khảo sát (lần đầu hoặc làm lại), ghi đè sở thích cũ và tính lại gợi ý sau vài giây.
   *
   * @param request câu trả lời của một lần làm hết nhánh mua cho bản thân
   * @param jwt token của Khách hàng
   * @return 200 kèm khảo sát sau khi lưu; 400 nếu câu trả lời sai, chưa trả lời hết hoặc là nhánh
   *     tìm quà
   */
  @PutMapping("/me")
  public MySurveyResponse saveMine(
      @Valid @RequestBody SurveyAnswersRequest request, @AuthenticationPrincipal Jwt jwt) {
    return surveyService.saveMine(accountId(jwt), request.answers());
  }

  /**
   * Ghi nhận Khách hàng bỏ qua khảo sát để không tự hiện lại; đã làm rồi thì giữ nguyên kết quả.
   *
   * @param jwt token của Khách hàng
   * @return 200 kèm khảo sát của tài khoản
   */
  @PostMapping("/me/skip")
  public MySurveyResponse skipMine(@AuthenticationPrincipal Jwt jwt) {
    return surveyService.skipMine(accountId(jwt));
  }

  private static long accountId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
