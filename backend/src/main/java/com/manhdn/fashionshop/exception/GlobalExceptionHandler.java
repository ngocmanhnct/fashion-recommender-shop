package com.manhdn.fashionshop.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Chuyển exception thành phản hồi lỗi thống nhất theo chuẩn RFC 9457 (ProblemDetail): mọi lỗi đều
 * có status, title, detail; lỗi validate có thêm "errors" (tên trường → thông báo).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Dữ liệu gửi lên không hợp lệ: 400 kèm danh sách lỗi theo từng trường. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ");
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    problem.setProperty("errors", errors);
    return problem;
  }

  /** Không tìm thấy dữ liệu được yêu cầu: 404. */
  @ExceptionHandler(NoSuchElementException.class)
  public ProblemDetail handleNotFound(NoSuchElementException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  /** Email đã tồn tại: 409. */
  @ExceptionHandler(EmailAlreadyExistsException.class)
  public ProblemDetail handleEmailExists(EmailAlreadyExistsException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  /** Xung đột với dữ liệu hiện có: 409. */
  @ExceptionHandler(ConflictException.class)
  public ProblemDetail handleConflict(ConflictException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  /** Sai thông tin đăng nhập: 401. */
  @ExceptionHandler(InvalidCredentialsException.class)
  public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  /** Đang bị khóa đăng nhập tạm thời: 429 kèm header Retry-After (giây). */
  @ExceptionHandler(TooManyLoginAttemptsException.class)
  public ResponseEntity<ProblemDetail> handleTooManyAttempts(TooManyLoginAttemptsException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter().toSeconds()))
        .body(problem);
  }
}
