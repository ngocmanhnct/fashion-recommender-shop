package com.manhdn.fashionshop.dto;

/**
 * Giá trị cột source của product_event_log và trường source của {@link RelatedProductResponse}. Sản
 * phẩm do ml-service gợi ý mang nguồn ml-service trả về (BEHAVIOR, SURVEY, RELATED).
 */
public final class RecommendationSource {

  /** Danh sách trang chủ tính theo hành vi (đạt ngưỡng BR-19). */
  public static final String BEHAVIOR = "BEHAVIOR";

  /** Danh sách tính theo khảo sát lần đầu. */
  public static final String SURVEY = "SURVEY";

  /** Theo quy tắc của backend, khi không gọi được ml-service. */
  public static final String RULE = "RULE";

  /** Sản phẩm bán chạy lấp chỗ trống (BR-22) hoặc thay cho danh sách chưa có. */
  public static final String POPULAR = "POPULAR";

  private RecommendationSource() {}
}
