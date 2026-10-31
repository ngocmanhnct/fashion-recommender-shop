package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Sửa hồ sơ cá nhân (chức năng 21). Email và vai trò không sửa được ở đây.
 *
 * @param fullName họ và tên, bắt buộc
 * @param gender giới tính, tùy chọn; null là bỏ thông tin đã khai (BR-03)
 * @param birthdate ngày sinh, tùy chọn, phải trong quá khứ; null là bỏ thông tin đã khai (BR-03)
 */
public record ProfileRequest(
    @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 255, message = "Họ và tên tối đa 255 ký tự")
        String fullName,
    Gender gender,
    @Past(message = "Ngày sinh phải trước ngày hôm nay") LocalDate birthdate) {}
