package com.manhdn.fashionshop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lượt hiện hoặc lượt bấm của các sản phẩm gợi ý, ghi vào product_event_log để tính tỷ lệ bấm.
 *
 * @param type SHOWN (khối gợi ý lọt vào màn hình, gửi cả danh sách) hoặc CLICKED (bấm 1 sản phẩm)
 * @param placement vị trí khối gợi ý: HOME (trang chủ), HOME_RECENT (hàng "Vì bạn vừa xem / vừa
 *     thêm vào giỏ" ở trang chủ), PRODUCT_DETAIL (sản phẩm liên quan), SURVEY_RESULT (kết quả khảo
 *     sát)
 * @param items các sản phẩm, tối đa 20
 */
public record RecommendationEventRequest(
    @NotNull(message = "Chưa có loại sự kiện")
        @Pattern(regexp = "SHOWN|CLICKED", message = "Loại sự kiện là SHOWN hoặc CLICKED")
        String type,
    @NotNull(message = "Chưa có vị trí gợi ý")
        @Pattern(
            regexp = "HOME|HOME_RECENT|PRODUCT_DETAIL|SURVEY_RESULT",
            message = "Vị trí gợi ý là HOME, HOME_RECENT, PRODUCT_DETAIL hoặc SURVEY_RESULT")
        String placement,
    @NotEmpty(message = "Chưa có sản phẩm nào")
        @Size(max = 20, message = "Tối đa 20 sản phẩm mỗi lần")
        List<@Valid @NotNull(message = "Sản phẩm không được để trống") Item> items) {

  /**
   * Một sản phẩm gợi ý.
   *
   * @param productId mã sản phẩm
   * @param source nguồn của sản phẩm, lấy từ trường source của API gợi ý
   * @param rank thứ hạng trong khối gợi ý, bắt đầu từ 1
   */
  public record Item(
      @NotNull(message = "Chưa có mã sản phẩm") @Positive(message = "Mã sản phẩm không hợp lệ")
          Long productId,
      @NotBlank(message = "Chưa có nguồn gợi ý")
          @Pattern(regexp = "[A-Z_]{1,30}", message = "Nguồn gợi ý không hợp lệ")
          String source,
      @NotNull(message = "Chưa có thứ hạng")
          @Min(value = 1, message = "Thứ hạng từ 1 đến 100")
          @Max(value = 100, message = "Thứ hạng từ 1 đến 100")
          Integer rank) {}
}
