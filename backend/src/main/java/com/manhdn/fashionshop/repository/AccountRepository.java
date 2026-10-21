package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Truy cập bảng account. */
public interface AccountRepository extends JpaRepository<Account, Long> {

  /** Tìm tài khoản theo email (email đã chuẩn hóa chữ thường). */
  Optional<Account> findByEmail(String email);

  /** Kiểm tra email đã có tài khoản chưa (BR-01). */
  boolean existsByEmail(String email);

  /**
   * Đọc tài khoản và khóa dòng (SELECT ... FOR UPDATE) đến hết giao dịch, để các thao tác đồng thời
   * của cùng một tài khoản (vd 2 lượt xem cùng lúc) chạy lần lượt.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Account> findWithLockById(Long id);
}
