package com.manhdn.fashionshop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Dữ liệu thêm sản phẩm mới (US-16, QĐ_QTV1): thông tin chung, tên tối đa 2 tầng phân loại và ít
 * nhất 1 biến thể (sản phẩm không có phân loại thì đúng 1 biến thể).
 *
 * @param name tên sản phẩm
 * @param description mô tả, tùy chọn
 * @param variationName1 tên tầng phân loại 1, vd "Màu sắc"; trống = sản phẩm không có phân loại
 * @param variationName2 tên tầng phân loại 2, vd "Kích cỡ"; trống = chỉ có 1 tầng
 * @param categoryId mã danh mục, bắt buộc
 * @param brandId mã thương hiệu, tùy chọn
 * @param imageUrls đường dẫn ảnh theo thứ tự hiển thị, tùy chọn
 * @param variants các biến thể, ít nhất 1
 */
public record ProductCreateRequest(
    @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
        String name,
    String description,
    @Size(max = 50, message = "Tên tầng phân loại tối đa 50 ký tự") String variationName1,
    @Size(max = 50, message = "Tên tầng phân loại tối đa 50 ký tự") String variationName2,
    @NotNull(message = "Phải chọn danh mục") Long categoryId,
    Long brandId,
    List<@NotBlank @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự") String> imageUrls,
    @NotEmpty(message = "Sản phẩm phải có ít nhất 1 biến thể")
        List<@Valid VariantRequest> variants) {}
