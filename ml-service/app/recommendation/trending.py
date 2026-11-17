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

`SalesHistory.snapshot` tính thêm các số liệu bán hàng khác tại 1 thời điểm (bán chạy theo nhiều
chu kỳ bán rã, theo nhóm tuổi người mua, sản phẩm mới hay cũ...), làm đặc trưng cho mô hình xếp
hạng (ranker.py).
"""

from array import array
from bisect import bisect_right
from collections.abc import Iterable, Mapping
from dataclasses import dataclass
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

SNAPSHOT_HALF_LIVES = (1.0, 3.0, 7.0, 14.0)
"""Chu kỳ bán rã (ngày) của các số "đang bán chạy" trong ảnh chụp số liệu bán hàng."""

AGE_BAND_EDGES = (20, 25, 30, 35, 40, 45, 50, 55, 60)
"""Ranh giới nhóm tuổi người mua: dưới 20, 20–24, ..., 55–59, từ 60."""
AGE_BANDS = len(AGE_BAND_EDGES) + 1

NEVER_SOLD_DAYS = 999.0
"""Số ngày ghi cho sản phẩm chưa bán lượt nào (thay cho "vô cùng")."""


def age_band(age: int | None) -> int:
    """Nhóm tuổi (0 đến AGE_BANDS − 1); -1 nếu không rõ tuổi."""
    if age is None or age <= 0:
        return -1
    return bisect_right(AGE_BAND_EDGES, age)


@dataclass(frozen=True)
class SalesSnapshot:
    """Số liệu bán hàng của mọi sản phẩm tại 1 thời điểm, chỉ từ lượt bán trước thời điểm đó.

    Mỗi mảng theo thứ tự hàng của vector sản phẩm; số lượt chưa lấy log.
    """

    decayed: dict[float, np.ndarray]
    """Chu kỳ bán rã (SNAPSHOT_HALF_LIVES) → D(j) trong TRENDING_WINDOW_DAYS ngày."""
    sold_7d: np.ndarray
    sold_prev_7d: np.ndarray
    """Số lượt bán từ 14 đến 7 ngày trước."""
    sold_all: np.ndarray
    buyers_7d: np.ndarray
    """Số người mua khác nhau trong 7 ngày."""
    days_since_first_sale: np.ndarray
    days_since_last_sale: np.ndarray
    category_trend: np.ndarray
    """D(j) chu kỳ 7 ngày cộng trên cả loại hàng (danh mục) của sản phẩm."""
    rank_in_category: np.ndarray
    """Thứ hạng theo D(j) 7 ngày trong danh mục chia cho số sản phẩm của danh mục: 0 là đứng đầu,
    càng gần 1 càng cuối."""
    rank_overall: np.ndarray
    """Thứ hạng theo D(j) 7 ngày trên cả cửa hàng (0 là đứng đầu)."""
    age_band_trend: np.ndarray
    """(AGE_BANDS, số sản phẩm): D(j) chu kỳ 7 ngày chỉ tính người mua thuộc từng nhóm tuổi."""
    by_trend: np.ndarray
    """Hàng sản phẩm có bán trong TRENDING_WINDOW_DAYS ngày, D(j) 7 ngày giảm dần."""
    by_age_band: tuple[np.ndarray, ...]
    by_category: tuple[np.ndarray, ...]
    """Theo chỉ số danh mục: hàng sản phẩm có bán gần đây trong danh mục, D(j) 7 ngày giảm dần."""


class SalesHistory:
    """Lượt bán theo thời gian (sắp theo lúc đặt), theo thứ tự hàng của vector sản phẩm."""

    def __init__(
        self,
        sales: Iterable[tuple],
        index: dict[int, int],
        size: int,
        bands: Mapping[int, int] | None = None,
    ) -> None:
        """`sales`: (mã sản phẩm, lúc đặt, số lượng) hoặc thêm mã tài khoản người mua ở cuối; bỏ
        sản phẩm không có trong `index`. `bands`: mã tài khoản → nhóm tuổi (`age_band`), để tính
        bán chạy theo nhóm tuổi.

        Lúc đặt và thời điểm gợi ý phải cùng kiểu: cùng không kèm múi giờ (giờ địa phương, như
        cột DATETIME của backend) hoặc cùng kèm múi giờ.
        """
        bands = bands or {}
        times, rows, quantities = array("d"), array("i"), array("d")
        accounts, groups = array("q"), array("b")
        for product_id, at, quantity, *rest in sales:
            row = index.get(product_id)
            if row is None:
                continue
            account = rest[0] if rest else -1
            times.append(at.timestamp())
            rows.append(row)
            quantities.append(quantity)
            accounts.append(account)
            groups.append(bands.get(account, -1))
        order = np.argsort(np.frombuffer(times, dtype=np.float64), kind="stable")
        self.times = np.frombuffer(times, dtype=np.float64)[order]
        self.rows = np.frombuffer(rows, dtype=np.int32)[order]
        self.quantities = np.frombuffer(quantities, dtype=np.float64)[order]
        self.accounts = np.frombuffer(accounts, dtype=np.int64)[order]
        self.bands = np.frombuffer(groups, dtype=np.int8)[order]
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

    def snapshot(self, now: datetime, categories: np.ndarray, category_count: int) -> SalesSnapshot:
        """Số liệu bán hàng tại `now` (lượt bán từ `now` trở đi không tính). `categories`: chỉ số
        danh mục (0 đến `category_count` − 1) của từng hàng sản phẩm."""
        size = self.size
        end = now.timestamp()
        start = end - TRENDING_WINDOW_DAYS * SECONDS_PER_DAY
        low, high = (int(i) for i in np.searchsorted(self.times, (start, end), side="left"))
        rows = self.rows[low:high]
        quantities = self.quantities[low:high]
        ages = (end - self.times[low:high]) / SECONDS_PER_DAY

        def count(mask: np.ndarray, weights: np.ndarray | None = None) -> np.ndarray:
            return np.bincount(
                rows[mask],
                weights=(quantities if weights is None else weights)[mask],
                minlength=size,
            )

        everything = np.ones(len(rows), dtype=bool)
        decayed = {
            h: count(everything, quantities * 0.5 ** (ages / h)) for h in SNAPSHOT_HALF_LIVES
        }
        week = ages < 7
        last_week = (ages >= 7) & (ages < 14)
        pairs = np.unique(self.accounts[low:high][week] * size + rows[week])
        buyers = np.bincount(pairs % size, minlength=size).astype(np.float64)
        sold_all = np.bincount(self.rows[:high], weights=self.quantities[:high], minlength=size)
        first = np.full(size, NEVER_SOLD_DAYS)
        last = np.full(size, NEVER_SOLD_DAYS)
        if high:
            seen, at = np.unique(self.rows[:high], return_index=True)  # lượt bán sớm nhất
            first[seen] = np.minimum((end - self.times[at]) / SECONDS_PER_DAY, NEVER_SOLD_DAYS)
            seen, at = np.unique(self.rows[:high][::-1], return_index=True)  # muộn nhất
            last[seen] = np.minimum(
                (end - self.times[:high][::-1][at]) / SECONDS_PER_DAY, NEVER_SOLD_DAYS
            )
        weekly = decayed[7.0]
        groups = self.bands[low:high]
        by_band = np.stack(
            [count(groups == band, quantities * 0.5 ** (ages / 7.0)) for band in range(AGE_BANDS)]
        )
        category_trend = np.bincount(categories, weights=weekly, minlength=category_count)
        by_trend = np.argsort(-weekly, kind="stable")
        rank_overall = np.empty(size)
        rank_overall[by_trend] = np.arange(size)
        rank_in_category = np.zeros(size)
        by_category = []
        for category in range(category_count):
            members = np.flatnonzero(categories == category)
            ordered = members[np.argsort(-weekly[members], kind="stable")]
            rank_in_category[ordered] = np.arange(len(ordered)) / max(len(ordered), 1)
            by_category.append(ordered[weekly[ordered] > 0])
        by_age_band = tuple(
            order[by_band[band][order] > 0]
            for band, order in enumerate(np.argsort(-by_band, axis=1, kind="stable"))
        )
        return SalesSnapshot(
            decayed=decayed,
            sold_7d=count(week),
            sold_prev_7d=count(last_week),
            sold_all=sold_all,
            buyers_7d=buyers,
            days_since_first_sale=first,
            days_since_last_sale=last,
            category_trend=category_trend[categories],
            rank_in_category=rank_in_category,
            rank_overall=rank_overall,
            age_band_trend=by_band,
            by_trend=by_trend[weekly[by_trend] > 0],
            by_age_band=by_age_band,
            by_category=tuple(by_category),
        )
