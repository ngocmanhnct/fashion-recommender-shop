package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.WishlistItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Truy cập bảng wishlist_item. */
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

  /** Các sản phẩm còn bán trong danh sách yêu thích, thêm sau đứng trước, nạp sẵn sản phẩm. */
  @Query(
      "SELECT w FROM WishlistItem w JOIN FETCH w.product p"
          + " WHERE w.account.id = :accountId AND p.deletedAt IS NULL"
          + " ORDER BY w.createdAt DESC, w.id DESC")
  List<WishlistItem> findListOf(long accountId);

  /** Mã các sản phẩm còn bán trong danh sách yêu thích, tăng dần. */
  @Query(
      "SELECT w.product.id FROM WishlistItem w"
          + " WHERE w.account.id = :accountId AND w.product.deletedAt IS NULL"
          + " ORDER BY w.product.id")
  List<Long> findProductIdsOf(long accountId);

  /** Dòng yêu thích của tài khoản cho một sản phẩm, nếu có. */
  Optional<WishlistItem> findByAccountIdAndProductId(long accountId, long productId);

  /** Số dòng yêu thích của tài khoản, kể cả sản phẩm đã ngừng bán. */
  long countByAccountId(long accountId);
}
