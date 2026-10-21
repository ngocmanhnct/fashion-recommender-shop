package com.manhdn.fashionshop.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Kiểm tra cách tách từ khóa và tạo mẫu LIKE, không cần CSDL. */
class ProductSpecificationsTest {

  @Test
  void tokenizeSplitsOnEveryNonLetterOrDigit() {
    assertThat(ProductSpecifications.tokenize("  Giày  thể-thao, NAM!! size42 "))
        .containsExactly("Giày", "thể", "thao", "NAM", "size42");
  }

  @Test
  void tokenizeDropsDuplicatesAndKeepsAtMostEightTokens() {
    assertThat(ProductSpecifications.tokenize("a b a c d e f g h i j"))
        .containsExactly("a", "b", "c", "d", "e", "f", "g", "h");
  }

  @Test
  void tokenizeReturnsNothingForNullOrSymbolsOnly() {
    assertThat(ProductSpecifications.tokenize(null)).isEmpty();
    assertThat(ProductSpecifications.tokenize(" %_\\ ")).isEmpty();
  }

  @Test
  void tokenizeComposesDecomposedVietnameseLetters() {
    assertThat(ProductSpecifications.tokenize("Áo thun")).containsExactly("Áo", "thun");
  }

  @Test
  void wordStartPatternEscapesLikeWildcards() {
    assertThat(ProductSpecifications.wordStartPattern("giày")).isEqualTo("% giày%");
    assertThat(ProductSpecifications.wordStartPattern("a%b_c\\")).isEqualTo("% a\\%b\\_c\\\\%");
  }
}
