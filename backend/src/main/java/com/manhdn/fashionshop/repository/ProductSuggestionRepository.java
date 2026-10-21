package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Product;
import java.util.List;

/** Truy vấn gợi ý sản phẩm khi tìm theo từ khóa không thấy kết quả (US-02). */
public interface ProductSuggestionRepository {

  /**
   * Sản phẩm đang bán, còn hàng, khớp ít nhất 1 từ của từ khóa (cách khớp như {@link
   * ProductSpecifications#matchesAllTokens}); khớp nhiều từ hơn đứng trước, rồi theo thứ tự "phổ
   * biến" (số đã bán giảm dần, vị trí trong danh mục, id).
   *
   * @param tokens các từ của từ khóa, xem {@link ProductSpecifications#tokenize}
   * @param limit số sản phẩm tối đa
   * @return các sản phẩm gợi ý, rỗng nếu không có từ nào hoặc không sản phẩm nào khớp
   */
  List<Product> findSuggestions(List<String> tokens, int limit);
}
