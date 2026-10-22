package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Brand;
import com.manhdn.fashionshop.entity.Category;
import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.ProductVariant;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.jpa.domain.Specification;

/**
 * Điều kiện lọc sản phẩm cho trang người dùng (US-01, US-02, US-12), dạng Specification của Spring
 * Data JPA để ghép tùy theo bộ lọc. Không điều kiện nào fetch-join, nên dùng được cho cả truy vấn
 * đếm khi phân trang.
 *
 * <p>Khớp từ khóa (US-02): mỗi từ của từ khóa phải khớp với <b>đầu một từ</b> trong tên sản phẩm,
 * tên danh mục hoặc tên thương hiệu. So bằng collation của cột (utf8mb4_0900_ai_ci) nên không phân
 * biệt hoa thường và dấu: "giay" khớp "Giày", "sneak" khớp "Sneaker", còn "áo" không khớp "Cao"
 * (khớp giữa từ sẽ lấy nhầm mọi sản phẩm có "cao", "thoáng", "cao cấp").
 */
public final class ProductSpecifications {

  /** Số từ tối đa của một từ khóa được dùng để tìm; các từ sau bị bỏ qua. */
  public static final int MAX_TOKENS = 8;

  /** Ranh giới giữa các từ khi tách từ khóa: mọi chuỗi ký tự không phải chữ hoặc số. */
  private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

  /**
   * Biểu thức chính quy của MySQL (ICU) cho cùng ranh giới đó, để tách từ trong cột giống hệt cách
   * tách từ khóa ở Java ({@code [:alnum:]} của ICU gồm cả chữ có dấu tiếng Việt).
   */
  private static final String NON_WORD_REGEX = "[^[:alnum:]]+";

  private static final char LIKE_ESCAPE = '\\';
  private static final String ID = "id";
  private static final String NAME = "name";
  private static final String PRICE = "price";
  private static final String PRODUCT = "product";
  private static final String CATEGORY = "category";
  private static final String BRAND = "brand";
  private static final String RATING_AVERAGE = "ratingAverage";

  private ProductSpecifications() {}

  /**
   * Tách từ khóa thành các từ để tìm: chuẩn hóa Unicode NFC, tách theo mọi ký tự không phải chữ
   * hoặc số, bỏ từ trùng, giữ tối đa {@link #MAX_TOKENS} từ đầu. Ký tự đặc biệt như {@code %},
   * {@code _} không bao giờ nằm trong từ, nên không thành ký tự đại diện của LIKE.
   *
   * @param keyword từ khóa người dùng nhập, có thể null
   * @return các từ theo thứ tự xuất hiện, rỗng nếu từ khóa null hoặc không có chữ, số nào
   */
  public static List<String> tokenize(String keyword) {
    if (keyword == null) {
      return List.of();
    }
    String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFC);
    return TOKEN_SEPARATOR
        .splitAsStream(normalized)
        .filter(token -> !token.isEmpty())
        .distinct()
        .limit(MAX_TOKENS)
        .toList();
  }

  /** Sản phẩm đang bán (chưa xóa mềm). */
  public static Specification<Product> forSale() {
    return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
  }

  /**
   * Mọi từ đều khớp đầu một từ của tên sản phẩm, danh mục hoặc thương hiệu (mỗi từ khớp ở cột nào
   * cũng được). Danh sách rỗng (từ khóa chỉ có ký tự đặc biệt) không khớp sản phẩm nào.
   */
  public static Specification<Product> matchesAllTokens(List<String> tokens) {
    return (root, query, cb) -> {
      if (tokens.isEmpty()) {
        return cb.disjunction();
      }
      KeywordColumns columns = KeywordColumns.of(root, cb);
      return cb.and(tokens.stream().map(columns::matches).toArray(Predicate[]::new));
    };
  }

  /** Thuộc danh mục cho trước. */
  public static Specification<Product> inCategory(Long categoryId) {
    return (root, query, cb) -> cb.equal(root.get(CATEGORY).get(ID), categoryId);
  }

  /** Thuộc một trong các danh mục cho trước (tập rỗng thì không khớp sản phẩm nào). */
  public static Specification<Product> inCategories(Collection<Long> categoryIds) {
    return (root, query, cb) ->
        categoryIds.isEmpty() ? cb.disjunction() : root.get(CATEGORY).get(ID).in(categoryIds);
  }

  /** Có gắn thương hiệu. */
  public static Specification<Product> hasBrand() {
    return (root, query, cb) -> cb.isNotNull(root.get(BRAND));
  }

  /** Không thuộc danh mục cho trước. */
  public static Specification<Product> notInCategory(Long categoryId) {
    return (root, query, cb) -> cb.notEqual(root.get(CATEGORY).get(ID), categoryId);
  }

  /** Thuộc thương hiệu cho trước. */
  public static Specification<Product> ofBrand(Long brandId) {
    return (root, query, cb) -> cb.equal(root.get(BRAND).get(ID), brandId);
  }

  /** Không gắn thương hiệu, hoặc gắn thương hiệu khác thương hiệu cho trước. */
  public static Specification<Product> notOfBrand(Long brandId) {
    return (root, query, cb) ->
        cb.or(cb.isNull(root.get(BRAND)), cb.notEqual(root.get(BRAND).get(ID), brandId));
  }

  /**
   * Có ít nhất 1 biến thể giá nằm trong khoảng (tính cả 2 đầu). Đầu nào null thì không giới hạn
   * phía đó.
   */
  public static Specification<Product> hasVariantPriceBetween(Long minPrice, Long maxPrice) {
    return (root, query, cb) ->
        existsVariant(
            root,
            query,
            cb,
            (variant, predicates) -> {
              if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(variant.<Long>get(PRICE), minPrice));
              }
              if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(variant.<Long>get(PRICE), maxPrice));
              }
            });
  }

  /**
   * Điểm đánh giá trung bình (đã làm tròn 1 chữ số thập phân, đúng giá trị hiển thị) từ mức cho
   * trước trở lên; sản phẩm chưa có đánh giá bị loại.
   */
  public static Specification<Product> ratingAtLeast(int rating) {
    return (root, query, cb) ->
        cb.greaterThanOrEqualTo(root.<Double>get(RATING_AVERAGE), (double) rating);
  }

  /**
   * Chưa có đánh giá nào, hoặc điểm trung bình (đã làm tròn 1 chữ số) từ mức cho trước trở lên.
   * Dùng cho Người cầu toàn (khảo sát lần đầu): sản phẩm chưa ai đánh giá chưa bị coi là kém.
   */
  public static Specification<Product> unratedOrRatingAtLeast(double rating) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get(RATING_AVERAGE)),
            cb.greaterThanOrEqualTo(root.<Double>get(RATING_AVERAGE), rating));
  }

  /** Còn hàng: có ít nhất 1 biến thể tồn kho lớn hơn 0. */
  public static Specification<Product> inStock() {
    return (root, query, cb) ->
        existsVariant(
            root,
            query,
            cb,
            (variant, predicates) -> predicates.add(cb.gt(variant.<Integer>get("stock"), 0)));
  }

  /** Mã sản phẩm không nằm trong tập cho trước (tập rỗng thì không loại gì). */
  public static Specification<Product> idNotIn(Collection<Long> ids) {
    return (root, query, cb) -> ids.isEmpty() ? cb.conjunction() : cb.not(root.get(ID).in(ids));
  }

  /**
   * Mẫu LIKE khớp từ ở đầu một từ: cột đã được thêm 1 khoảng trắng ở đầu và mọi ranh giới từ đổi
   * thành khoảng trắng, nên chỉ cần tìm "khoảng trắng + từ". Ký tự {@code \}, {@code %}, {@code _}
   * trong từ được thoát để so đúng nguyên văn.
   */
  static String wordStartPattern(String token) {
    String escaped =
        token
            .replace(String.valueOf(LIKE_ESCAPE), "\\\\")
            .replace("%", LIKE_ESCAPE + "%")
            .replace("_", LIKE_ESCAPE + "_");
    return "% " + escaped + "%";
  }

  private static Predicate existsVariant(
      Root<Product> root,
      CriteriaQuery<?> query,
      CriteriaBuilder cb,
      VariantConditions conditions) {
    Subquery<Long> subquery = query.subquery(Long.class);
    Root<ProductVariant> variant = subquery.from(ProductVariant.class);
    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.equal(variant.get(PRODUCT), root));
    conditions.addTo(variant, predicates);
    subquery.select(variant.get(ID)).where(predicates.toArray(Predicate[]::new));
    return cb.exists(subquery);
  }

  /** Thêm điều kiện cho biến thể trong truy vấn con EXISTS. */
  @FunctionalInterface
  private interface VariantConditions {
    void addTo(Root<ProductVariant> variant, List<Predicate> predicates);
  }

  /**
   * Các cột tên dùng để khớp từ khóa trong một truy vấn (tên sản phẩm, tên danh mục, tên thương
   * hiệu qua LEFT JOIN vì thương hiệu có thể trống), đã đổi sang dạng "khoảng trắng + các từ cách
   * nhau 1 khoảng trắng".
   */
  static final class KeywordColumns {

    private final CriteriaBuilder cb;
    private final List<Expression<String>> words;

    private KeywordColumns(CriteriaBuilder cb, List<Expression<String>> words) {
      this.cb = cb;
      this.words = words;
    }

    /** Tạo các join cần thiết trên root của truy vấn; gọi 1 lần cho mỗi truy vấn. */
    static KeywordColumns of(Root<Product> root, CriteriaBuilder cb) {
      Join<Product, Category> category = root.join(CATEGORY);
      Join<Product, Brand> brand = root.join(BRAND, JoinType.LEFT);
      return new KeywordColumns(
          cb,
          List.of(
              words(cb, root.get(NAME)),
              words(cb, category.get(NAME)),
              words(cb, brand.get(NAME))));
    }

    /** Từ khớp đầu một từ ở ít nhất 1 trong các cột. */
    Predicate matches(String token) {
      String pattern = wordStartPattern(token);
      return cb.or(
          words.stream().map(w -> cb.like(w, pattern, LIKE_ESCAPE)).toArray(Predicate[]::new));
    }

    private static Expression<String> words(CriteriaBuilder cb, Expression<String> column) {
      Expression<String> separated =
          cb.function(
              "REGEXP_REPLACE", String.class, column, cb.literal(NON_WORD_REGEX), cb.literal(" "));
      return cb.concat(cb.literal(" "), separated);
    }
  }
}
