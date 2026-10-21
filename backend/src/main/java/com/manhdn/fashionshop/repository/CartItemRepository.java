package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.CartItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Truy cập bảng cart_item. */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

  /** Các dòng giỏ hàng của tài khoản theo thứ tự thêm vào, nạp sẵn biến thể và sản phẩm. */
  @Query(
      "SELECT c FROM CartItem c JOIN FETCH c.variant v JOIN FETCH v.product"
          + " WHERE c.account.id = :accountId ORDER BY c.id")
  List<CartItem> findCartOf(long accountId);
}
