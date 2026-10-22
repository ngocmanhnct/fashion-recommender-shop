package com.manhdn.fashionshop.dto;

/**
 * Một dòng trong giỏ hàng, giá lấy theo giá hiện tại của biến thể (không phải giá lúc thêm vào giỏ,
 * US-10).
 *
 * @param variantId mã biến thể, cũng là khóa của dòng trong giỏ hàng
 * @param productId mã sản phẩm, để mở trang chi tiết
 * @param productName tên sản phẩm
 * @param variantLabel nhãn phân loại, vd "Đen – 42"; null nếu sản phẩm không có phân loại
 * @param thumbnailUrl ảnh đầu tiên của sản phẩm, có thể null
 * @param categoryName tên danh mục, để vẽ ảnh minh họa khi sản phẩm chưa có ảnh
 * @param unitPrice đơn giá hiện tại, VNĐ
 * @param quantity số lượng trong giỏ
 * @param stock tồn kho hiện tại của biến thể (chỉ để tham khảo, BR-08)
 * @param lineTotal thành tiền = đơn giá × số lượng, VNĐ
 */
public record CartItemResponse(
    Long variantId,
    Long productId,
    String productName,
    String variantLabel,
    String thumbnailUrl,
    String categoryName,
    long unitPrice,
    int quantity,
    int stock,
    long lineTotal) {}
