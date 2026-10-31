package com.manhdn.fashionshop.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewResponseTest {

  @Test
  void displayNameIsTheLastWordOrAnonymous() {
    assertThat(ReviewResponse.displayName("  Đoàn  Ngọc Mạnh ")).isEqualTo("Mạnh");
    assertThat(ReviewResponse.displayName("Lan")).isEqualTo("Lan");
    assertThat(ReviewResponse.displayName("   ")).isEqualTo("Khách hàng");
    assertThat(ReviewResponse.displayName(null)).isEqualTo("Khách hàng");
  }
}
