package com.manhdn.fashionshop.dto;

import java.util.List;

/**
 * Giỏ hàng hiện tại (US-07) kèm các thay đổi hệ thống vừa tự làm (BR-11). Phí vận chuyển và mã giảm
 * giá tính ở bước thanh toán, không có ở đây.
 *
 * @param items các dòng còn mua được, theo thứ tự thêm vào giỏ
 * @param notices các dòng vừa bị loại hoặc giảm số lượng, để báo cho khách; rỗng nếu không có
 * @param totalQuantity tổng số lượng sản phẩm, dùng cho số trên biểu tượng giỏ hàng
 * @param subtotal tạm tính = tổng thành tiền các dòng, VNĐ
 */
public record CartResponse(
    List<CartItemResponse> items, List<CartNotice> notices, int totalQuantity, long subtotal) {}
