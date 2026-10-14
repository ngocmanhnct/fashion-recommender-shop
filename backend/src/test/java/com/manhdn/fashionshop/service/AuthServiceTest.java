package com.manhdn.fashionshop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.manhdn.fashionshop.dto.AuthResponse;
import com.manhdn.fashionshop.dto.LoginRequest;
import com.manhdn.fashionshop.dto.RegisterRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Gender;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.exception.EmailAlreadyExistsException;
import com.manhdn.fashionshop.exception.InvalidCredentialsException;
import com.manhdn.fashionshop.exception.TooManyLoginAttemptsException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.security.JwtService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private AccountRepository accountRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private LoginAttemptService loginAttemptService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    authService =
        new AuthService(accountRepository, passwordEncoder, jwtService, loginAttemptService);
  }

  @Test
  void registerCreatesCustomerWithNormalizedEmailAndHashedPassword() {
    when(accountRepository.existsByEmail("a@x.vn")).thenReturn(false);
    when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(i -> i.getArgument(0));
    when(jwtService.issueToken(any())).thenReturn("token");
    when(jwtService.getExpirationSeconds()).thenReturn(86_400L);

    AuthResponse response =
        authService.register(
            new RegisterRequest(
                "  A@X.vn ", "matkhau1", " Nguyễn Văn A ", Gender.MALE, LocalDate.of(2004, 1, 1)));

    ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("a@x.vn");
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
    assertThat(saved.getValue().getFullName()).isEqualTo("Nguyễn Văn A");
    assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
    assertThat(response.accessToken()).isEqualTo("token");
    assertThat(response.tokenType()).isEqualTo("Bearer");
  }

  @Test
  void registerRejectsExistingEmail() {
    when(accountRepository.existsByEmail("a@x.vn")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(request("a@x.vn")))
        .isInstanceOf(EmailAlreadyExistsException.class);
    verify(accountRepository, never()).saveAndFlush(any());
  }

  @Test
  void registerTurnsUniqueViolationIntoEmailExists() {
    when(accountRepository.existsByEmail("a@x.vn")).thenReturn(false);
    when(accountRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("uk"));

    assertThatThrownBy(() -> authService.register(request("a@x.vn")))
        .isInstanceOf(EmailAlreadyExistsException.class);
  }

  @Test
  void loginSucceedsAndResetsCounter() {
    Account account = new Account("a@x.vn", "stored-hash", "A", Role.CUSTOMER);
    when(accountRepository.findByEmail("a@x.vn")).thenReturn(Optional.of(account));
    when(passwordEncoder.matches("matkhau1", "stored-hash")).thenReturn(true);
    when(jwtService.issueToken(account)).thenReturn("token");

    AuthResponse response = authService.login(new LoginRequest("A@x.vn", "matkhau1"));

    assertThat(response.accessToken()).isEqualTo("token");
    verify(loginAttemptService).recordSuccess("a@x.vn");
  }

  @Test
  void wrongPasswordAndUnknownEmailGiveSameErrorAndCountFailure() {
    Account account = new Account("a@x.vn", "stored-hash", "A", Role.CUSTOMER);
    when(accountRepository.findByEmail("a@x.vn")).thenReturn(Optional.of(account));
    when(accountRepository.findByEmail("khongco@x.vn")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(new LoginRequest("a@x.vn", "saimatkhau1")))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Email hoặc mật khẩu không đúng");
    assertThatThrownBy(() -> authService.login(new LoginRequest("khongco@x.vn", "saimatkhau1")))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Email hoặc mật khẩu không đúng");
    verify(loginAttemptService).recordFailure("a@x.vn");
    verify(loginAttemptService).recordFailure("khongco@x.vn");
    verify(passwordEncoder).matches("saimatkhau1", "hashed");
  }

  @Test
  void lockedEmailIsRejectedBeforeCheckingPassword() {
    doThrow(new TooManyLoginAttemptsException(Duration.ofMinutes(15)))
        .when(loginAttemptService)
        .checkNotLocked("a@x.vn");

    assertThatThrownBy(() -> authService.login(new LoginRequest("a@x.vn", "matkhau1")))
        .isInstanceOf(TooManyLoginAttemptsException.class);
    verify(accountRepository, never()).findByEmail(anyString());
  }

  private static RegisterRequest request(String email) {
    return new RegisterRequest(email, "matkhau1", "A", null, null);
  }
}
