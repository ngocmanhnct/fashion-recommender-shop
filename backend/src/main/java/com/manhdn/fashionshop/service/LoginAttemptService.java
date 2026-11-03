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

  /**
   * Số email nhớ tối đa trước khi dọn những email không còn gì cần nhớ, để thử hàng loạt email ngẫu
   * nhiên không làm đầy bộ nhớ.
   */
  static final int MAX_TRACKED_EMAILS = 10_000;

  /** Thời gian chờ khi có quá nhiều lần đăng nhập cùng lúc cho một email. */
  private static final Duration BUSY_RETRY_AFTER = Duration.ofMinutes(1);

  private static final Duration CLEAN_UP_INTERVAL = Duration.ofMinutes(1);

  private final Clock clock;
  private Instant lastCleanUp;
  private final Map<String, AttemptState> states = new ConcurrentHashMap<>();

  /** Nhận đồng hồ để test điều khiển được thời gian. */
  public LoginAttemptService(Clock clock) {
    this.clock = clock;
  }

  /**
   * Bắt đầu một lần đăng nhập: chặn lại nếu email đang bị khóa, rồi giữ chỗ cho lần thử này trước
   * khi kiểm tra mật khẩu (BCrypt mất vài chục ms). Nhờ giữ chỗ, gửi song song nhiều request cũng
   * chỉ thử được tối đa {@value #MAX_FAILED_ATTEMPTS} mật khẩu trước khi bị khóa. Sau hàm này phải
   * gọi đúng 1 trong {@link #recordFailure}, {@link #recordSuccess} hoặc {@link #release}.
   *
   * @param email email đã chuẩn hóa
   * @throws TooManyLoginAttemptsException nếu email đang bị khóa hoặc đang có quá nhiều lần thử
   */
  public void startAttempt(String email) {
    Instant now = clock.instant();
    AttemptState state = states.computeIfAbsent(email, key -> new AttemptState());
    synchronized (state) {
      if (state.lockedUntil != null) {
        if (now.isBefore(state.lockedUntil)) {
          throw new TooManyLoginAttemptsException(Duration.between(now, state.lockedUntil));
        }
        state.lockedUntil = null;
      }
      dropOldFailures(state, now);
      if (state.failures.size() + state.inFlight >= MAX_FAILED_ATTEMPTS) {
        throw new TooManyLoginAttemptsException(BUSY_RETRY_AFTER);
      }
      state.inFlight++;
    }
  }

  /**
   * Bỏ chỗ đã giữ khi lần đăng nhập dừng giữa chừng vì lỗi khác (vd mất kết nối CSDL), không tính
   * là sai mật khẩu.
   *
   * @param email email đã chuẩn hóa
   */
  public void release(String email) {
    AttemptState state = states.get(email);
    if (state != null) {
      synchronized (state) {
        state.inFlight = Math.max(0, state.inFlight - 1);
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
      state.inFlight = Math.max(0, state.inFlight - 1);
      if (state.lockedUntil != null && !now.isBefore(state.lockedUntil)) {
        state.lockedUntil = null;
      }
      state.failures.addLast(now);
      dropOldFailures(state, now);
      if (state.failures.size() >= MAX_FAILED_ATTEMPTS) {
        state.lockedUntil = now.plus(LOCK_DURATION);
        state.failures.clear();
      }
    }
    cleanUpIfTooMany(now);
  }

  /**
   * Đăng nhập thành công thì xóa bộ đếm của email.
   *
   * @param email email đã chuẩn hóa
   */
  public void recordSuccess(String email) {
    states.remove(email);
  }

  /**
   * Quá {@value #MAX_TRACKED_EMAILS} email thì dọn email không còn gì cần nhớ, tối đa 1 lần mỗi
   * phút: khi bị thử hàng loạt, các email vừa thử chưa dọn được ngay, quét lại mỗi lần sai chỉ tốn
   * CPU.
   */
  private void cleanUpIfTooMany(Instant now) {
    if (states.size() <= MAX_TRACKED_EMAILS) {
      return;
    }
    synchronized (this) {
      if (lastCleanUp != null && now.isBefore(lastCleanUp.plus(CLEAN_UP_INTERVAL))) {
        return;
      }
      lastCleanUp = now;
    }
    states.entrySet().removeIf(entry -> entry.getValue().idle(now));
  }

  private static void dropOldFailures(AttemptState state, Instant now) {
    Instant windowStart = now.minus(ATTEMPT_WINDOW);
    while (!state.failures.isEmpty() && !state.failures.peekFirst().isAfter(windowStart)) {
      state.failures.pollFirst();
    }
  }

  /** Số lần sai, số lần đang kiểm tra mật khẩu và mốc hết khóa của một email. */
  private static final class AttemptState {
    private final Deque<Instant> failures = new ArrayDeque<>();
    private Instant lockedUntil;
    private int inFlight;

    /** Không còn gì cần nhớ: không khóa, không lần thử đang chạy, mọi lần sai đã ra khỏi cửa sổ. */
    synchronized boolean idle(Instant now) {
      dropOldFailures(this, now);
      return inFlight == 0
          && failures.isEmpty()
          && (lockedUntil == null || !now.isBefore(lockedUntil));
    }
  }
}
