package com.manhdn.fashionshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Dữ liệu sửa thông tin chung của sản phẩm. Biến thể sửa qua API biến thể riêng. Tên các tầng phân
 * loại đổi được, nhưng số tầng phải giữ nguyên.
 *
 * @param name tên sản phẩm
 * @param description mô tả, tùy chọn
 * @param variationName1 tên tầng phân loại 1, trống nếu sản phẩm không có phân loại
 * @param variationName2 tên tầng phân loại 2, trống nếu sản phẩm có ít hơn 2 tầng
 * @param categoryId mã danh mục, bắt buộc
 * @param brandId mã thương hiệu, tùy chọn
 * @param imageUrls toàn bộ ảnh mới theo thứ tự hiển thị, thay hết ảnh cũ; null hoặc rỗng = xóa ảnh
 */
public record ProductUpdateRequest(
    @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
        String name,
    String description,
    @Size(max = 50, message = "Tên tầng phân loại tối đa 50 ký tự") String variationName1,
    @Size(max = 50, message = "Tên tầng phân loại tối đa 50 ký tự") String variationName2,
    @NotNull(message = "Phải chọn danh mục") Long categoryId,
    Long brandId,
    List<@NotBlank @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự") String>
        imageUrls) {}
