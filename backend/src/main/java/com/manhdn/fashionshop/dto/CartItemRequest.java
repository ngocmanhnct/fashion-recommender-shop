package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.service.CartService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Một biến thể kèm số lượng: thêm vào giỏ hàng (US-06), hoặc một dòng giỏ hàng của khách chưa đăng
 * nhập gửi lên để xem trước hoặc gộp (BR-06, BR-07).
 *
 * @param variantId mã biến thể
 * @param quantity số lượng, từ 1 đến {@link CartService#MAX_QUANTITY}
 */
public record CartItemRequest(
    @NotNull(message = "Chưa chọn phân loại sản phẩm") Long variantId,
    @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải từ 1 trở lên")
        @Max(value = CartService.MAX_QUANTITY, message = "Số lượng tối đa 99")
        Integer quantity) {}
