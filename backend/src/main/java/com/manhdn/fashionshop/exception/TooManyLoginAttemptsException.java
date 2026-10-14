package com.manhdn.fashionshop.exception;

import java.time.Duration;

/** Email đang bị khóa đăng nhập tạm thời sau nhiều lần sai (Bảng 2.8 mục 2). Trả về HTTP 429. */
public class TooManyLoginAttemptsException extends RuntimeException {

  private final transient Duration retryAfter;

  /** Tạo exception kèm thời gian còn phải chờ. */
  public TooManyLoginAttemptsException(Duration retryAfter) {
    super(
        "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau "
            + Math.max(1, (retryAfter.toSeconds() + 59) / 60)
            + " phút");
    this.retryAfter = retryAfter;
  }

  /** Thời gian còn phải chờ trước khi được thử lại. */
  public Duration getRetryAfter() {
    return retryAfter;
  }
}
