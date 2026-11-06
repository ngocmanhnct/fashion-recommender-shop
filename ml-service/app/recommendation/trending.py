"""Điểm "đang bán chạy": số đã bán gần đây, lượt bán càng mới càng nặng.

Tại thời điểm gợi ý t, mỗi lượt bán (đơn khác CANCELLED) của sản phẩm j trong TRENDING_WINDOW_DAYS
ngày trước t được nhân 0,5^(tuổi / chu kỳ bán rã):

    D(j) = Σ số lượng · 0,5^((t − lúc đặt) / h)
    trend(j) = log(1 + D(j)) / log(1 + D_max) · min(1, D_max / TRENDING_FULL_VOLUME)

Khác "bán chạy" (đếm mọi đơn từ trước tới nay), điểm này đi theo xu hướng vài ngày qua: trên dữ liệu
mua hàng thật của H&M, bán chạy 7 ngày gần nhất đoán đúng hơn gấp đôi bán chạy mọi thời điểm
(data/hm/ket-qua.md). Thừa số cuối giữ cho cửa hàng còn ít đơn: khi sản phẩm bán chạy nhất mấy ngày
qua chưa tới TRENDING_FULL_VOLUME lượt, vài lượt bán lẻ chỉ là ngẫu nhiên chứ chưa phải xu hướng,
nên điểm của mọi sản phẩm thu nhỏ theo tỷ lệ.
"""

from array import array
from collections.abc import Iterable
from datetime import datetime

import numpy as np

TRENDING_WINDOW_DAYS = 30
"""Chỉ tính lượt bán trong chừng này ngày trước thời điểm gợi ý. Với chu kỳ bán rã 7 ngày, lượt bán
30 ngày trước chỉ còn khoảng 5% trọng số."""

TRENDING_FULL_VOLUME = 20.0
"""Số lượt bán (đã giảm dần) của sản phẩm bán chạy nhất để điểm đang bán chạy có đủ trọng lượng.
Mẫu 5% khách H&M có khoảng 120–160 lượt nên không bị thu nhỏ; dữ liệu mô phỏng của cửa hàng chỉ
khoảng 2–3 lượt (thu nhỏ còn khoảng 1/8)."""

SECONDS_PER_DAY = 86_400


class SalesHistory:
    """Lượt bán theo thời gian (sắp theo lúc đặt), theo thứ tự hàng của vector sản phẩm."""

    def __init__(
        self, sales: Iterable[tuple[int, datetime, int]], index: dict[int, int], size: int
    ) -> None:
        """`sales`: (mã sản phẩm, lúc đặt, số lượng); bỏ sản phẩm không có trong `index`.

        Lúc đặt và thời điểm gợi ý phải cùng kiểu: cùng không kèm múi giờ (giờ địa phương, như
        cột DATETIME của backend) hoặc cùng kèm múi giờ.
        """
        times, rows, quantities = array("d"), array("i"), array("d")
        for product_id, at, quantity in sales:
            row = index.get(product_id)
            if row is None:
                continue
            times.append(at.timestamp())
            rows.append(row)
            quantities.append(quantity)
        order = np.argsort(np.frombuffer(times, dtype=np.float64), kind="stable")
        self.times = np.frombuffer(times, dtype=np.float64)[order]
        self.rows = np.frombuffer(rows, dtype=np.int32)[order]
        self.quantities = np.frombuffer(quantities, dtype=np.float64)[order]
        self.size = size

    def trending(self, now: datetime, half_life_days: float) -> np.ndarray:
        """trend(j) của mọi sản phẩm tại `now`, từ 0 đến 1. Lượt bán từ `now` trở đi không tính."""
        end = now.timestamp()
        start = end - TRENDING_WINDOW_DAYS * SECONDS_PER_DAY
        low, high = np.searchsorted(self.times, (start, end), side="left")
        ages = (end - self.times[low:high]) / SECONDS_PER_DAY
        decayed = np.bincount(
            self.rows[low:high],
            weights=self.quantities[low:high] * 0.5 ** (ages / half_life_days),
            minlength=self.size,
        )
        top = float(decayed.max(initial=0.0))
        if top <= 0:
            return decayed
        return np.log1p(decayed) / np.log1p(top) * min(1.0, top / TRENDING_FULL_VOLUME)
