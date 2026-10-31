package com.manhdn.fashionshop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Hồ sơ cá nhân, đổi mật khẩu và quên mật khẩu (chức năng 21, BR-48) qua HTTP với MySQL thật. Không
 * chạy trong transaction: email chỉ gửi sau khi giao dịch commit, nên dữ liệu được xóa ở cuối mỗi
 * test. Máy chủ email là bản giả.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {"spring.mail.host=smtp.test", "app.frontend-url=https://shop.test/"})
class AccountIntegrationTest {

  private static final String DOMAIN = "@hoso.test";
  private static final Pattern LINK =
      Pattern.compile("https://shop\\.test/reset-password\\?token=([A-Za-z0-9_-]+)");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private JavaMailSender mailSender;

  @AfterEach
  void cleanUp() {
    jdbc.update(
        "DELETE t FROM password_reset_token t JOIN account a ON a.id = t.account_id"
            + " WHERE a.email LIKE ?",
        "%" + DOMAIN);
    jdbc.update("DELETE FROM account WHERE email LIKE ?", "%" + DOMAIN);
  }

  @Test
  void customerUpdatesProfileAndInvalidValuesAreRejected() throws Exception {
    String token = register("hoso");

    profile(
            token,
            "{\"fullName\":\"  Trần Thị B  \",\"gender\":\"FEMALE\",\"birthdate\":\"2001-02-03\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Trần Thị B"))
        .andExpect(jsonPath("$.gender").value("FEMALE"))
        .andExpect(jsonPath("$.birthdate").value("2001-02-03"))
        .andExpect(jsonPath("$.email").value("hoso" + DOMAIN))
        .andExpect(jsonPath("$.role").value("CUSTOMER"));
    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.fullName").value("Trần Thị B"));

    // Để trống giới tính, ngày sinh là bỏ thông tin đã khai.
    profile(token, "{\"fullName\":\"Trần Thị B\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gender").isEmpty())
        .andExpect(jsonPath("$.birthdate").isEmpty());

    profile(token, "{\"fullName\":\" \",\"birthdate\":\"2999-01-01\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.fullName").value("Họ và tên không được để trống"))
        .andExpect(jsonPath("$.errors.birthdate").value("Ngày sinh phải trước ngày hôm nay"));
    mockMvc
        .perform(
            put("/api/auth/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"A\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void changePasswordNeedsCurrentPasswordAndLocksAfterFiveWrongTries() throws Exception {
    String token = register("doimk");

    changePassword(token, "saimatkhau9", "matkhaumoi2")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Mật khẩu hiện tại không đúng"));
    changePassword(token, "matkhau1", "matkhau1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Mật khẩu mới phải khác mật khẩu hiện tại"));
    changePassword(token, "matkhau1", "ngan1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.newPassword").value("Mật khẩu phải từ 8 đến 72 ký tự"));

    changePassword(token, "matkhau1", "matkhaumoi2").andExpect(status().isNoContent());
    login("doimk", "matkhau1").andExpect(status().isUnauthorized());
    login("doimk", "matkhaumoi2").andExpect(status().isOk());

    String other = register("doimk2");
    for (int i = 0; i < 5; i++) {
      changePassword(other, "saimatkhau9", "matkhaumoi2").andExpect(status().isBadRequest());
    }
    changePassword(other, "matkhau1", "matkhaumoi2").andExpect(status().isTooManyRequests());
  }

  @Test
  void forgotPasswordEmailsSingleUseLinkAndStoresOnlyItsHash() throws Exception {
    register("quenmk");

    forgot("QUENMK" + DOMAIN).andExpect(status().isAccepted());
    SimpleMailMessage message = sentMessage();
    assertThat(message.getTo()).containsExactly("quenmk" + DOMAIN);
    assertThat(message.getSubject()).isEqualTo("Đặt lại mật khẩu Fashion Shop");
    assertThat(message.getText()).contains("Khách quenmk").contains("hết hạn sau 30 phút");
    String resetToken = tokenIn(message);

    List<String> hashes =
        jdbc.queryForList(
            "SELECT t.token_hash FROM password_reset_token t JOIN account a ON a.id = t.account_id"
                + " WHERE a.email = ?",
            String.class,
            "quenmk" + DOMAIN);
    assertThat(hashes).containsExactly(sha256(resetToken));

    reset(resetToken, "matkhaumoi3").andExpect(status().isNoContent());
    login("quenmk", "matkhau1").andExpect(status().isUnauthorized());
    login("quenmk", "matkhaumoi3").andExpect(status().isOk());

    reset(resetToken, "matkhaumoi4")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(startsWith("Liên kết")));
    reset("khong-co-ma-nay", "matkhaumoi4").andExpect(status().isBadRequest());
  }

  @Test
  void unknownEmailGetsSameAnswerAndResendIsLimited() throws Exception {
    forgot("khongco" + DOMAIN).andExpect(status().isAccepted());
    verify(mailSender, after(300).never()).send(any(SimpleMailMessage.class));

    register("gioihan");
    forgot("gioihan" + DOMAIN).andExpect(status().isAccepted());
    forgot("gioihan" + DOMAIN).andExpect(status().isAccepted());
    verify(mailSender, timeout(3000).times(1)).send(any(SimpleMailMessage.class));
    forgot("sai-dinh-dang").andExpect(status().isBadRequest());
  }

  @Test
  void expiredLinkAndLinksOlderThanAPasswordChangeAreRejected() throws Exception {
    String token = register("hethan");
    forgot("hethan" + DOMAIN).andExpect(status().isAccepted());
    String expired = tokenIn(sentMessage());
    jdbc.update(
        "UPDATE password_reset_token SET expires_at = created_at - INTERVAL 1 MINUTE"
            + " WHERE token_hash = ?",
        sha256(expired));
    reset(expired, "matkhaumoi5").andExpect(status().isBadRequest());

    jdbc.update(
        "UPDATE password_reset_token SET created_at = created_at - INTERVAL 2 MINUTE"
            + " WHERE token_hash = ?",
        sha256(expired));
    clearInvocations(mailSender);
    forgot("hethan" + DOMAIN).andExpect(status().isAccepted());
    String fresh = tokenIn(sentMessage());
    changePassword(token, "matkhau1", "matkhaumoi6").andExpect(status().isNoContent());
    reset(fresh, "matkhaumoi7").andExpect(status().isBadRequest());
  }

  @Test
  void resetUnlocksLoginLockedByWrongPasswords() throws Exception {
    register("mokhoa");
    for (int i = 0; i < 5; i++) {
      login("mokhoa", "saimatkhau9").andExpect(status().isUnauthorized());
    }
    login("mokhoa", "matkhau1").andExpect(status().isTooManyRequests());

    forgot("mokhoa" + DOMAIN).andExpect(status().isAccepted());
    reset(tokenIn(sentMessage()), "matkhaumoi8").andExpect(status().isNoContent());
    login("mokhoa", "matkhaumoi8").andExpect(status().isOk());
  }

  private SimpleMailMessage sentMessage() {
    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender, timeout(3000)).send(captor.capture());
    return captor.getValue();
  }

  private static String tokenIn(SimpleMailMessage message) {
    Matcher matcher = LINK.matcher(message.getText());
    assertThat(matcher.find()).as("email có liên kết đặt lại mật khẩu").isTrue();
    return matcher.group(1);
  }

  private static String sha256(String text) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
  }

  private String register(String name) throws Exception {
    String body =
        """
        {"email":"%s%s","password":"matkhau1","fullName":"Khách %s"}
        """
            .formatted(name, DOMAIN, name);
    ResultActions result =
        mockMvc
            .perform(
                post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
  }

  private ResultActions login(String name, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s%s\",\"password\":\"%s\"}".formatted(name, DOMAIN, password)));
  }

  private ResultActions profile(String token, String body) throws Exception {
    return mockMvc.perform(
        put("/api/auth/me")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions changePassword(String token, String current, String next) throws Exception {
    return mockMvc.perform(
        put("/api/auth/me/password")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
  }

  private ResultActions forgot(String email) throws Exception {
    return mockMvc.perform(
        post("/api/auth/forgot-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\"}".formatted(email)));
  }

  private ResultActions reset(String token, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/reset-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, password)));
  }
}
