package com.manhdn.fashionshop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.manhdn.fashionshop.exception.TooManyLoginAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

  private static final String EMAIL = "a@x.vn";

  private MutableClock clock;
  private LoginAttemptService service;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-10-14T08:00:00Z"));
    service = new LoginAttemptService(clock);
  }

  @Test
  void fourFailuresDoNotLock() {
    failTimes(4);
    assertThatCode(() -> service.checkNotLocked(EMAIL)).doesNotThrowAnyException();
  }

  @Test
  void fifthFailureWithinWindowLocks() {
    failTimes(5);
    assertThatThrownBy(() -> service.checkNotLocked(EMAIL))
        .isInstanceOf(TooManyLoginAttemptsException.class);
  }

  @Test
  void lockExpiresAfterFifteenMinutes() {
    failTimes(5);
    clock.advance(Duration.ofMinutes(15));
    assertThatCode(() -> service.checkNotLocked(EMAIL)).doesNotThrowAnyException();
  }

  @Test
  void failuresOlderThanWindowAreNotCounted() {
    failTimes(4);
    clock.advance(Duration.ofMinutes(15));
    service.recordFailure(EMAIL);
    assertThatCode(() -> service.checkNotLocked(EMAIL)).doesNotThrowAnyException();
  }

  @Test
  void successResetsCounter() {
    failTimes(4);
    service.recordSuccess(EMAIL);
    failTimes(4);
    assertThatCode(() -> service.checkNotLocked(EMAIL)).doesNotThrowAnyException();
  }

  @Test
  void lockIsPerEmail() {
    failTimes(5);
    assertThatCode(() -> service.checkNotLocked("b@x.vn")).doesNotThrowAnyException();
  }

  @Test
  void lockReportsRemainingTime() {
    for (int i = 0; i < 5; i++) {
      service.recordFailure(EMAIL);
    }
    clock.advance(Duration.ofMinutes(10));
    assertThatThrownBy(() -> service.checkNotLocked(EMAIL))
        .isInstanceOfSatisfying(
            TooManyLoginAttemptsException.class,
            ex -> assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofMinutes(5)));
  }

  private void failTimes(int times) {
    for (int i = 0; i < times; i++) {
      service.recordFailure(EMAIL);
      clock.advance(Duration.ofSeconds(1));
    }
  }

  /** Đồng hồ giả, tua được thời gian. */
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
