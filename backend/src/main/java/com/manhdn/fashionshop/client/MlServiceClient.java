package com.manhdn.fashionshop.client;

import com.manhdn.fashionshop.config.MlServiceProperties;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gọi API gợi ý tính ngay của ml-service qua mạng nội bộ (trình duyệt không gọi thẳng ml-service).
 * Mọi lỗi (ml-service tắt, quá hạn, trả lỗi) đều trả {@link Optional#empty()} để nơi gọi dùng gợi ý
 * theo quy tắc thay thế (BR-22), không làm hỏng trang của khách.
 */
@Component
public class MlServiceClient {

  private static final Logger log = LoggerFactory.getLogger(MlServiceClient.class);
  private static final ParameterizedTypeReference<List<MlSuggestion>> SUGGESTIONS =
      new ParameterizedTypeReference<>() {};

  private final boolean enabled;
  private final RestClient restClient;

  /** Dựng client với địa chỉ và thời gian chờ trong cấu hình. */
  public MlServiceClient(MlServiceProperties properties) {
    this.enabled = properties.enabled();
    // HTTP/1.1: mặc định HttpClient thử nâng lên HTTP/2 (h2c) trên http thường, uvicorn không hỗ
    // trợ nên bỏ mất thân request POST.
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.timeout())
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(properties.timeout());
    this.restClient =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
  }

  /**
   * Sản phẩm liên quan của một sản phẩm (US-12): giống về nội dung và hay được mua cùng.
   *
   * @param productId mã sản phẩm đang xem
   * @param limit số sản phẩm tối đa
   * @return danh sách theo thứ tự gợi ý, hoặc rỗng nếu không gọi được ml-service
   */
  public Optional<List<MlSuggestion>> related(long productId, int limit) {
    return call(
        "sản phẩm liên quan",
        () ->
            restClient
                .get()
                .uri("/recommendations/related/{id}?limit={limit}", productId, limit)
                .retrieve()
                .body(SUGGESTIONS));
  }

  /**
   * Gợi ý theo hồ sơ khảo sát (US-23, công thức 1.4).
   *
   * @param request hồ sơ khảo sát
   * @return danh sách theo thứ tự gợi ý, hoặc rỗng nếu không gọi được ml-service
   */
  public Optional<List<MlSuggestion>> forSurvey(SurveyRequest request) {
    return call(
        "gợi ý theo khảo sát",
        () ->
            restClient
                .post()
                .uri("/recommendations/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SUGGESTIONS));
  }

  private Optional<List<MlSuggestion>> call(String what, Request request) {
    if (!enabled) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(request.send());
    } catch (RestClientException e) {
      log.warn("Không gọi được ml-service ({}), dùng gợi ý theo quy tắc: {}", what, e.getMessage());
      return Optional.empty();
    }
  }

  /** Một lần gọi ml-service. */
  @FunctionalInterface
  private interface Request {
    List<MlSuggestion> send();
  }

  /**
   * Một sản phẩm ml-service gợi ý.
   *
   * @param productId mã sản phẩm
   * @param score điểm gợi ý, càng cao càng phù hợp
   * @param reason lý do ngắn hiển thị cho khách
   * @param source nguồn gợi ý: BEHAVIOR, SURVEY hoặc RELATED
   */
  public record MlSuggestion(long productId, double score, String reason, String source) {}

  /**
   * Hồ sơ khảo sát gửi sang ml-service.
   *
   * @param categoryIds các danh mục quan tâm (ít nhất 1)
   * @param brandId thương hiệu ưa thích, có thể null
   * @param priceMin giá thấp nhất, VNĐ
   * @param priceMax giá cao nhất, VNĐ; null = không giới hạn
   * @param style SAVER, BRAND, QUALITY, TREND; null = không cộng điểm theo phong cách
   * @param minRating ngưỡng điểm đánh giá, có thể null
   * @param excludeProductIds sản phẩm không gợi ý
   * @param limit số sản phẩm tối đa
   */
  public record SurveyRequest(
      List<Long> categoryIds,
      Long brandId,
      long priceMin,
      Long priceMax,
      String style,
      Double minRating,
      List<Long> excludeProductIds,
      int limit) {}
}
