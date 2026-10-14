package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Dữ liệu form đăng ký (US-04, QĐ_KH4). Cố ý không có trường role: client không thể tự chọn vai trò
 * (BR-04); JSON gửi kèm "role" bị bỏ qua.
 *
 * @param email email đăng nhập, bắt buộc, đúng định dạng
 * @param password mật khẩu, tối thiểu 8 ký tự, có ít nhất 1 chữ cái và 1 chữ số (BR-02)
 * @param fullName họ và tên, bắt buộc
 * @param gender giới tính, tùy chọn (BR-03)
 * @param birthdate ngày sinh, tùy chọn, phải trong quá khứ (BR-03)
 */
public record RegisterRequest(
    @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,
    @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        @Pattern(
            regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
            message = "Mật khẩu phải có ít nhất 1 chữ cái và 1 chữ số")
        String password,
    @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 255, message = "Họ và tên tối đa 255 ký tự")
        String fullName,
    Gender gender,
    @Past(message = "Ngày sinh phải trước ngày hôm nay") LocalDate birthdate) {}
