package com.manhdn.fashionshop.service;

/**
 * Kiểm định z cho hiệu hai tỷ lệ (hai phía), dùng để so sánh 2 nhóm của thử nghiệm A/B (BR-46).
 *
 * <p>Với x1/n1 và x2/n2: p = (x1 + x2) / (n1 + n2), SE = sqrt(p(1 - p)(1/n1 + 1/n2)), z = (x1/n1 -
 * x2/n2) / SE, giá trị p = 2(1 - Φ(|z|)). Φ tính bằng xấp xỉ hàm sai số của Abramowitz và Stegun
 * (7.1.26, sai số dưới 1,5e-7).
 */
public final class TwoProportionTest {

  private TwoProportionTest() {}

  /**
   * Giá trị p hai phía của giả thuyết "hai tỷ lệ bằng nhau".
   *
   * @param x1 số lần xảy ra ở nhóm 1
   * @param n1 cỡ nhóm 1, lớn hơn 0
   * @param x2 số lần xảy ra ở nhóm 2
   * @param n2 cỡ nhóm 2, lớn hơn 0
   * @return giá trị p trong [0, 1]; 1 khi cả 2 nhóm cùng tỷ lệ 0% hoặc 100%
   */
  public static double probabilityOfEqualRates(long x1, long n1, long x2, long n2) {
    if (n1 <= 0 || n2 <= 0) {
      throw new IllegalArgumentException("Cỡ nhóm phải lớn hơn 0");
    }
    double pooled = (double) (x1 + x2) / (n1 + n2);
    double se = Math.sqrt(pooled * (1 - pooled) * (1.0 / n1 + 1.0 / n2));
    if (se == 0) {
      return 1;
    }
    double z = ((double) x1 / n1 - (double) x2 / n2) / se;
    return Math.min(1, Math.max(0, 2 * (1 - normalCdf(Math.abs(z)))));
  }

  /** Hàm phân phối tích lũy của phân phối chuẩn tắc. */
  static double normalCdf(double z) {
    return 0.5 * (1 + erf(z / Math.sqrt(2)));
  }

  private static double erf(double x) {
    double sign = Math.signum(x);
    double a = Math.abs(x);
    double t = 1 / (1 + 0.3275911 * a);
    double poly =
        t
            * (0.254829592
                + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
    return sign * (1 - poly * Math.exp(-a * a));
  }
}
