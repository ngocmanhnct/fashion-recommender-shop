package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy cập bảng password_reset_token (BR-48). */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  /**
   * Liên kết theo bản băm, khóa dòng (SELECT … FOR UPDATE) để 2 lần bấm cùng lúc không cùng đặt lại
   * mật khẩu bằng 1 liên kết.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM PasswordResetToken t JOIN FETCH t.account WHERE t.tokenHash = :hash")
  Optional<PasswordResetToken> findForUpdate(@Param("hash") String tokenHash);

  /** Số liên kết đã tạo cho tài khoản từ thời điểm since (giới hạn số email gửi đi). */
  long countByAccountIdAndCreatedAtAfter(Long accountId, LocalDateTime since);

  /** Đánh dấu mọi liên kết còn dùng được của tài khoản là đã dùng (sau khi đổi mật khẩu). */
  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE PasswordResetToken t SET t.usedAt = :now"
          + " WHERE t.account.id = :accountId AND t.usedAt IS NULL")
  int markAllUsed(@Param("accountId") Long accountId, @Param("now") LocalDateTime now);
}
