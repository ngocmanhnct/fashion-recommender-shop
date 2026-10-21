package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.repository.ProductSpecifications.KeywordColumns;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;

/**
 * Cài đặt {@link ProductSuggestionRepository} bằng Criteria API (Spring Data tự gắn vào {@link
 * ProductRepository} nhờ hậu tố Impl). Điểm khớp của một sản phẩm là số từ khớp, tính bằng tổng các
 * {@code CASE WHEN <từ khớp> THEN 1 ELSE 0 END}.
 */
class ProductSuggestionRepositoryImpl implements ProductSuggestionRepository {

  private final EntityManager entityManager;

  /** Nhận EntityManager dùng chung của Spring qua constructor. */
  ProductSuggestionRepositoryImpl(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<Product> findSuggestions(List<String> tokens, int limit) {
    if (tokens.isEmpty()) {
      return List.of();
    }
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Product> query = cb.createQuery(Product.class);
    Root<Product> root = query.from(Product.class);
    KeywordColumns columns = KeywordColumns.of(root, cb);
    List<Predicate> matches = tokens.stream().map(columns::matches).toList();
    Expression<Integer> matchedTokens =
        matches.stream()
            .map(match -> cb.<Integer>selectCase().when(match, 1).otherwise(0))
            .reduce(cb::sum)
            .orElseThrow();
    query
        .select(root)
        .where(
            ProductSpecifications.forSale().toPredicate(root, query, cb),
            ProductSpecifications.inStock().toPredicate(root, query, cb),
            cb.or(matches.toArray(Predicate[]::new)))
        .orderBy(
            cb.desc(matchedTokens),
            cb.desc(root.get("soldCount")),
            cb.asc(root.get("categoryRank")),
            cb.asc(root.get("id")));
    return entityManager.createQuery(query).setMaxResults(limit).getResultList();
  }
}
