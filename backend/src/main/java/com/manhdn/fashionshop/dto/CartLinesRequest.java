package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Giỏ hàng lưu ở trình duyệt của khách chưa đăng nhập (BR-06), gửi lên để xem trước giá và tồn kho
 * hiện tại, hoặc để gộp vào giỏ hàng của tài khoản ngay sau khi đăng nhập (BR-07).
 *
 * @param items các dòng giỏ hàng, tối đa {@link CartService#MAX_LINES} dòng
 */
public record CartLinesRequest(
    @NotNull(message = "Danh sách sản phẩm không được để trống")
        @Size(max = CartService.MAX_LINES, message = "Giỏ hàng tối đa 50 sản phẩm")
        List<@NotNull(message = "Dòng giỏ hàng không được để trống") @Valid CartItemRequest>
            items) {}
