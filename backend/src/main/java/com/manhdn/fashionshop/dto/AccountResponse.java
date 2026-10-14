package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Gender;
import com.manhdn.fashionshop.entity.Role;
import java.time.LocalDate;

/**
 * Thông tin tài khoản trả về cho client, không có mật khẩu băm.
 *
 * @param id mã tài khoản
 * @param email email
 * @param fullName họ và tên
 * @param gender giới tính, có thể null
 * @param birthdate ngày sinh, có thể null
 * @param role vai trò
 */
public record AccountResponse(
    Long id, String email, String fullName, Gender gender, LocalDate birthdate, Role role) {

  /** Chuyển entity sang DTO. */
  public static AccountResponse from(Account account) {
    return new AccountResponse(
        account.getId(),
        account.getEmail(),
        account.getFullName(),
        account.getGender(),
        account.getBirthdate(),
        account.getRole());
  }
}
