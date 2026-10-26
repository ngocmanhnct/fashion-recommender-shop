package com.manhdn.fashionshop.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.manhdn.fashionshop.client.MlServiceClient.MlSuggestion;
import com.manhdn.fashionshop.client.MlServiceClient.SurveyRequest;
import com.manhdn.fashionshop.config.MlServiceProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Kiểm tra gọi ml-service qua HTTP thật với một máy chủ giả chạy trong test. */
class MlServiceClientTest {

  private static final String SUGGESTIONS =
      """
      [{"productId": 7, "score": 0.81, "reason": "Cùng thương hiệu Biti's", "source": "RELATED"}]
      """;

  private HttpServer server;
  private final AtomicReference<String> lastRequest = new AtomicReference<>();
  private final AtomicReference<String> lastBody = new AtomicReference<>();
  private final AtomicReference<String> lastUpgrade = new AtomicReference<>();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void relatedReadsSuggestions() {
    respond("/recommendations/related", 200, SUGGESTIONS, Duration.ZERO);

    assertThat(client(true).related(5, 4))
        .contains(List.of(new MlSuggestion(7, 0.81, "Cùng thương hiệu Biti's", "RELATED")));
    assertThat(lastRequest.get()).isEqualTo("GET /recommendations/related/5?limit=4");
  }

  @Test
  void surveySendsCamelCaseJson() {
    respond("/recommendations/survey", 200, SUGGESTIONS, Duration.ZERO);
    SurveyRequest request =
        new SurveyRequest(List.of(1L, 2L), null, 200_000, null, "SAVER", 4.0, List.of(), 8);

    assertThat(client(true).forSurvey(request)).isPresent();
    assertThat(lastRequest.get()).isEqualTo("POST /recommendations/survey");
    assertThat(lastBody.get())
        .contains("\"categoryIds\":[1,2]", "\"priceMin\":200000", "\"style\":\"SAVER\"")
        .contains("\"minRating\":4.0", "\"limit\":8");
    // Không xin nâng lên HTTP/2: uvicorn của ml-service sẽ bỏ mất thân request.
    assertThat(lastUpgrade.get()).isNull();
  }

  @Test
  void errorsAndSlowAnswersGiveNothing() {
    respond("/recommendations/related/404", 404, "{\"detail\":\"Không có\"}", Duration.ZERO);
    respond("/recommendations/related/9", 200, SUGGESTIONS, Duration.ofSeconds(2));

    MlServiceClient client = client(true);
    assertThat(client.related(404, 4)).isEmpty();
    assertThat(client.related(9, 4)).isEmpty();
  }

  @Test
  void unreachableOrDisabledServiceGivesNothing() {
    respond("/recommendations/related", 200, SUGGESTIONS, Duration.ZERO);
    assertThat(client(false).related(5, 4)).isEmpty();
    assertThat(lastRequest.get()).isNull();

    server.stop(0);
    assertThat(client(true).related(5, 4)).isEmpty();
  }

  private MlServiceClient client(boolean enabled) {
    String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    return new MlServiceClient(new MlServiceProperties(enabled, baseUrl, Duration.ofMillis(300)));
  }

  private void respond(String path, int status, String body, Duration delay) {
    server.createContext(
        path,
        exchange -> {
          record(exchange);
          sleep(delay);
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
  }

  private void record(HttpExchange exchange) throws IOException {
    lastRequest.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
    lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    lastUpgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
  }

  private static void sleep(Duration delay) {
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
