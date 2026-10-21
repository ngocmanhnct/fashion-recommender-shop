package com.manhdn.fashionshop.service;

import java.sql.SQLException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Chạy một đoạn nghiệp vụ trong giao dịch đọc-ghi riêng và tự thử lại cả giao dịch khi MySQL báo
 * khóa chéo (lỗi 1213) hoặc chờ khóa quá lâu (lỗi 1205). Dùng cho các thao tác ghi đồng thời của
 * người dùng: ghi lượt xem, giỏ hàng, đặt hàng (BR-08 "thử lại tối đa 1-2 lần", BR-20).
 *
 * <p>Tối đa {@link #MAX_ATTEMPTS} lần chạy: lần đầu và 2 lần thử lại. Chỉ thử lại được khi chính
 * lớp này mở giao dịch ngoài cùng; gọi trong một giao dịch đang chạy thì báo lỗi, vì lỗi khóa đã
 * đánh dấu cả giao dịch ngoài phải hủy nên chạy lại bên trong không có tác dụng. Đoạn nghiệp vụ
 * phải tạo mọi entity bên trong callback: entity tạo từ lần chạy trước đã nhận id rồi bị hủy theo
 * giao dịch, dùng lại sẽ thành cập nhật thay vì thêm mới.
 */
@Component
public class TransactionRetry {

  /** Số lần chạy tối đa: lần đầu và 2 lần thử lại. */
  public static final int MAX_ATTEMPTS = 3;

  private static final int DEADLOCK = 1213;
  private static final int LOCK_WAIT_TIMEOUT = 1205;
  private static final Logger log = LoggerFactory.getLogger(TransactionRetry.class);

  private final TransactionTemplate transactionTemplate;

  /** Tạo TransactionTemplate đọc-ghi từ transaction manager của ứng dụng. */
  public TransactionRetry(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * Chạy callback trong giao dịch riêng, thử lại khi gặp lỗi khóa của MySQL.
   *
   * @param callback đoạn nghiệp vụ, tạo mọi entity bên trong
   * @param <T> kiểu kết quả
   * @return kết quả của lần chạy thành công
   * @throws IllegalStateException nếu đang có giao dịch chạy
   * @throws RuntimeException exception của lần chạy cuối, hoặc của lần chạy đầu tiên gặp lỗi không
   *     phải lỗi khóa
   */
  public <T> T execute(Supplier<T> callback) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "TransactionRetry phải mở giao dịch ngoài cùng, không gọi trong giao dịch đang chạy");
    }
    for (int attempt = 1; ; attempt++) {
      try {
        return transactionTemplate.execute(status -> callback.get());
      } catch (RuntimeException ex) {
        if (attempt >= MAX_ATTEMPTS || !isLockFailure(ex)) {
          throw ex;
        }
        log.warn("Lỗi khóa của MySQL, thử lại giao dịch lần {}: {}", attempt, ex.getMessage());
      }
    }
  }

  /**
   * Chạy đoạn nghiệp vụ không trả kết quả, như {@link #execute}.
   *
   * @param action đoạn nghiệp vụ, tạo mọi entity bên trong
   * @throws IllegalStateException nếu đang có giao dịch chạy
   */
  public void run(Runnable action) {
    execute(
        () -> {
          action.run();
          return null;
        });
  }

  /** Trong chuỗi nguyên nhân có SQLException mã 1213 (khóa chéo) hoặc 1205 (chờ khóa quá lâu). */
  static boolean isLockFailure(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof SQLException sql
          && (sql.getErrorCode() == DEADLOCK || sql.getErrorCode() == LOCK_WAIT_TIMEOUT)) {
        return true;
      }
    }
    return false;
  }
}
