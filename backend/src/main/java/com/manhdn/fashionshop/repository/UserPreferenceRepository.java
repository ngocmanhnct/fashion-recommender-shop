package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.UserPreference;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Truy cập bảng user_preference. */
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

  /** Sở thích của tài khoản. */
  List<UserPreference> findByAccountId(Long accountId);

  /** Xóa mọi sở thích của tài khoản, dùng khi làm lại khảo sát (BR-37). */
  @Modifying(flushAutomatically = true, clearAutomatically = false)
  @Query("DELETE FROM UserPreference p WHERE p.account.id = :accountId")
  void deleteByAccountId(Long accountId);
}
