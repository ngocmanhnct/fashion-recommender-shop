package com.manhdn.fashionshop.dto;

/**
 * Thông báo về một dòng giỏ hàng mà hệ thống đã tự sửa (BR-11): bị loại vì ngừng bán hoặc hết hàng,
 * hoặc bị giảm số lượng cho bằng tồn kho.
 *
 * @param variantId mã biến thể của dòng bị sửa
 * @param productName tên sản phẩm; null nếu biến thể không còn tồn tại
 * @param variantLabel nhãn phân loại, có thể null
 * @param type loại thay đổi
 * @param message nội dung hiển thị cho khách
 */
public record CartNotice(
    Long variantId, String productName, String variantLabel, Type type, String message) {

  /** Loại thay đổi hệ thống đã tự làm với một dòng giỏ hàng. */
  public enum Type {
    /** Sản phẩm đã ngừng bán (xóa mềm) hoặc biến thể không còn: dòng bị loại. */
    REMOVED_UNAVAILABLE,
    /** Biến thể đã hết hàng: dòng bị loại. */
    REMOVED_OUT_OF_STOCK,
    /** Tồn kho ít hơn số lượng trong giỏ: số lượng được giảm cho bằng tồn kho. */
    QUANTITY_REDUCED,
    /** Giỏ hàng đã đủ số dòng tối đa nên dòng này không được gộp vào. */
    NOT_MERGED_CART_FULL
  }
}
