package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Truy cập bảng product_variant. */
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

  /** Mã SKU đã được dùng chưa. */
  boolean existsBySkuCode(String skuCode);

  /** Mã SKU đã thuộc về một biến thể khác chưa, dùng khi sửa biến thể. */
  boolean existsBySkuCodeAndIdNot(String skuCode, Long id);

  /**
   * Đọc và khóa các biến thể (SELECT ... FOR UPDATE) đến hết giao dịch, theo thứ tự mã tăng dần để
   * các giao dịch đặt hàng, hủy đơn khóa cùng thứ tự, không chờ vòng lẫn nhau. Hai đơn cùng mua một
   * biến thể vì vậy chạy lần lượt, đơn sau thấy tồn kho đã trừ (BR-08).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT v FROM ProductVariant v WHERE v.id IN :ids ORDER BY v.id")
  List<ProductVariant> findAllForUpdate(Collection<Long> ids);
}
