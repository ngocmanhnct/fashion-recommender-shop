package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.service.CartService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Số lượng mới của một dòng giỏ hàng (US-07).
 *
 * @param quantity số lượng, từ 1 đến {@link CartService#MAX_QUANTITY}; muốn bỏ dòng thì gọi API xóa
 */
public record CartQuantityRequest(
    @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải từ 1 trở lên")
        @Max(value = CartService.MAX_QUANTITY, message = "Số lượng tối đa 99")
        Integer quantity) {}
