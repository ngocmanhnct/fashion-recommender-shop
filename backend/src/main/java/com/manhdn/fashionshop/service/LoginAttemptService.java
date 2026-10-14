package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.exception.TooManyLoginAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Chống dò mật khẩu (Bảng 2.8 mục 2): cùng một email đăng nhập sai {@value #MAX_FAILED_ATTEMPTS}
 * lần trong 15 phút thì bị khóa đăng nhập 15 phút.
 *
 * <p>Đếm theo email người dùng gõ vào, kể cả email chưa có tài khoản, để phản hồi của email có thật
 * và email không tồn tại giống hệt nhau. Trạng thái giữ trong bộ nhớ của tiến trình backend: khởi
 * động lại thì xóa hết, và chỉ đúng khi chạy 1 instance backend (đúng với kiến trúc triển khai hiện
 * tại). Nếu sau này chạy nhiều instance thì chuyển sang lưu dùng chung.
 */
@Service
public class LoginAttemptService {

  /** Số lần sai tối đa trong một cửa sổ thời gian. */
  public static final int MAX_FAILED_ATTEMPTS = 5;

  /** Cửa sổ đếm số lần sai. */
  public static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

  /** Thời gian khóa sau khi vượt ngưỡng. */
  public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

  private final Clock clock;
  private final Map<String, AttemptState> states = new ConcurrentHashMap<>();

  /** Nhận đồng hồ để test điều khiển được thời gian. */
  public LoginAttemptService(Clock clock) {
    this.clock = clock;
  }

  /**
   * Chặn lại nếu email đang bị khóa. Gọi trước khi kiểm tra mật khẩu, để khi đang khóa thì kể cả
   * mật khẩu đúng cũng không đăng nhập được.
   *
   * @param email email đã chuẩn hóa
   * @throws TooManyLoginAttemptsException nếu email đang bị khóa
   */
  public void checkNotLocked(String email) {
    AttemptState state = states.get(email);
    if (state == null) {
      return;
    }
    Instant now = clock.instant();
    synchronized (state) {
      if (state.lockedUntil != null && now.isBefore(state.lockedUntil)) {
        throw new TooManyLoginAttemptsException(Duration.between(now, state.lockedUntil));
      }
    }
  }

  /**
   * Ghi nhận một lần đăng nhập sai; đủ {@value #MAX_FAILED_ATTEMPTS} lần trong cửa sổ thì khóa.
   *
   * @param email email đã chuẩn hóa
   */
  public void recordFailure(String email) {
    Instant now = clock.instant();
    AttemptState state = states.computeIfAbsent(email, key -> new AttemptState());
    synchronized (state) {
      if (state.lockedUntil != null && !now.isBefore(state.lockedUntil)) {
        state.lockedUntil = null;
      }
      state.failures.addLast(now);
      Instant windowStart = now.minus(ATTEMPT_WINDOW);
      while (!state.failures.isEmpty() && !state.failures.peekFirst().isAfter(windowStart)) {
        state.failures.pollFirst();
      }
      if (state.failures.size() >= MAX_FAILED_ATTEMPTS) {
        state.lockedUntil = now.plus(LOCK_DURATION);
        state.failures.clear();
      }
    }
  }

  /**
   * Đăng nhập thành công thì xóa bộ đếm của email.
   *
   * @param email email đã chuẩn hóa
   */
  public void recordSuccess(String email) {
    states.remove(email);
  }

  /** Số lần sai và mốc hết khóa của một email. */
  private static final class AttemptState {
    private final Deque<Instant> failures = new ArrayDeque<>();
    private Instant lockedUntil;
  }
}
