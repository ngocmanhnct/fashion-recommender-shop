package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu tạo hoặc đổi tên danh mục, thương hiệu.
 *
 * @param name tên, bắt buộc, tối đa 100 ký tự (cột VARCHAR(100))
 */
public record NameRequest(
    @NotBlank(message = "Tên không được để trống")
        @Size(max = 100, message = "Tên tối đa 100 ký tự")
        String name) {}
