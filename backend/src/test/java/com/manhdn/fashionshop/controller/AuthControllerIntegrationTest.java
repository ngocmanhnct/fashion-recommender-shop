package com.manhdn.fashionshop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kiểm tra trọn luồng qua HTTP với MySQL thật (Flyway + Hibernate validate): đăng ký, đăng nhập,
 * khóa sau 5 lần sai, và phân quyền theo JWT. Mỗi test chạy trong transaction và rollback ở cuối.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(AuthControllerIntegrationTest.AdminProbe.class)
class AuthControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void registerCreatesCustomerAndReturnsToken() throws Exception {
    register("dangky@x.vn", "matkhau1")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(86400))
        .andExpect(jsonPath("$.account.email").value("dangky@x.vn"))
        .andExpect(jsonPath("$.account.role").value("CUSTOMER"))
        .andExpect(jsonPath("$.account.passwordHash").doesNotExist());

    Account saved = accountRepository.findByEmail("dangky@x.vn").orElseThrow();
    assertThat(saved.getPasswordHash()).startsWith("$2a$10$");
  }

  @Test
  void roleSentByClientIsIgnored() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"leoquyen@x.vn","password":"matkhau1","fullName":"B","role":"ADMIN"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.account.role").value("CUSTOMER"));
  }

  @Test
  void weakPasswordAndBadEmailAreRejected() throws Exception {
    register("khong-phai-email", "chicochu")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.email").exists())
        .andExpect(
            jsonPath("$.errors.password").value("Mật khẩu phải có ít nhất 1 chữ cái và 1 chữ số"));
  }

  @Test
  void duplicateEmailIsRejectedCaseInsensitive() throws Exception {
    register("trung@x.vn", "matkhau1").andExpect(status().isCreated());
    register("TRUNG@x.vn", "matkhau2")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Email đã được sử dụng cho một tài khoản khác"));
  }

  @Test
  void loginReturnsTokenThatOpensMe() throws Exception {
    register("dangnhap@x.vn", "matkhau1").andExpect(status().isCreated());

    String token = tokenOf(login("dangnhap@x.vn", "matkhau1").andExpect(status().isOk()));

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("dangnhap@x.vn"));
  }

  @Test
  void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
    register("sai@x.vn", "matkhau1").andExpect(status().isCreated());

    login("sai@x.vn", "saimatkhau1")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng"));
    login("khongtontai@x.vn", "saimatkhau1")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng"));
  }

  @Test
  void fiveFailuresLockEvenTheCorrectPassword() throws Exception {
    register("khoa@x.vn", "matkhau1").andExpect(status().isCreated());
    for (int i = 0; i < 5; i++) {
      login("khoa@x.vn", "saimatkhau1").andExpect(status().isUnauthorized());
    }

    login("khoa@x.vn", "matkhau1")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  void requestsWithoutValidTokenGet401() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer khong.phai.token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminApiIsForbiddenForCustomerAndOpenForAdmin() throws Exception {
    String customerToken =
        tokenOf(register("khach@x.vn", "matkhau1").andExpect(status().isCreated()));
    mockMvc
        .perform(get("/api/admin/probe").header("Authorization", "Bearer " + customerToken))
        .andExpect(status().isForbidden());

    accountRepository.save(
        new Account("quantri@x.vn", passwordEncoder.encode("matkhau1"), "QTV", Role.ADMIN));
    String adminToken = tokenOf(login("quantri@x.vn", "matkhau1").andExpect(status().isOk()));
    mockMvc
        .perform(get("/api/admin/probe").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  void healthStaysPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  private ResultActions register(String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"%s","password":"%s","fullName":"Nguyễn Văn A","gender":"MALE",\
                "birthdate":"2004-05-20"}
                """
                    .formatted(email, password)));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
  }

  private static String tokenOf(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
  }

  /** API giả dưới /api/admin, chỉ có trong test, để kiểm tra quy tắc phân quyền. */
  @TestConfiguration
  static class AdminProbe {

    @RestController
    static class ProbeController {
      @GetMapping("/api/admin/probe")
      String probe() {
        return "ok";
      }
    }
  }
}
