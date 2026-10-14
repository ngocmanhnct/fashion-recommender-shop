package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng account. */
public interface AccountRepository extends JpaRepository<Account, Long> {

  /** Tìm tài khoản theo email (email đã chuẩn hóa chữ thường). */
  Optional<Account> findByEmail(String email);

  /** Kiểm tra email đã có tài khoản chưa (BR-01). */
  boolean existsByEmail(String email);
}
