"""Test phân nhóm khách hàng không cần CSDL: chọn K, đặt tên nhóm, gán khách vào nhóm."""

import random
from datetime import UTC, datetime

import numpy as np

from app.segmentation import model
from app.segmentation.model import KScore, choose_k, elbow_point, format_money, name_segments, train
from app.segmentation.rfm import Rfm, recency_days

# (R ngày, F đơn, M VNĐ) điển hình của 4 kiểu khách rõ rệt.
GROUPS = {
    "VIP": (5, 15, 12_000_000),
    "NEW": (10, 1, 300_000),
    "LOST": (300, 2, 500_000),
    "LOYAL": (20, 6, 2_500_000),
}


def make_customers(per_group: int = 15, seed: int = 1) -> tuple[list[Rfm], dict[int, str]]:
    """Khách quanh tâm của từng nhóm (lệch ngẫu nhiên ±20%)."""
    rng = random.Random(seed)
    rows, truth = [], {}
    for code, (r, f, m) in GROUPS.items():
        for _ in range(per_group):
            account_id = len(rows) + 1
            rows.append(
                Rfm(
                    account_id,
                    max(0, round(r * rng.uniform(0.8, 1.2))),
                    max(1, round(f * rng.uniform(0.8, 1.2))),
                    round(m * rng.uniform(0.8, 1.2)),
                )
            )
            truth[account_id] = code
    return rows, truth


def test_train_finds_and_names_clear_groups():
    rows, truth = make_customers()
    trained = train(rows)
    assert trained is not None
    assert trained.customer_count == 60
    assert len(trained.segments) == 4
    assert trained.silhouette >= model.MIN_SILHOUETTE
    assert len({s.code for s in trained.segments}) == len(trained.segments)
    assigned = trained.assign(rows)
    # Mỗi nhóm thật nằm trọn trong 1 nhóm tìm được, và tên nhóm khớp kiểu khách.
    for code in ("VIP", "NEW", "LOST"):
        found = {
            s.code for row, s in zip(rows, assigned, strict=True) if truth[row.account_id] == code
        }
        assert found == {code}
    assert sum(s.size for s in trained.segments) == 60
    vip = next(s for s in trained.segments if s.code == "VIP")
    assert vip.mean_frequency > 10
    assert "Gợi ý:" in vip.description and "đơn đã giao" in vip.description


def test_train_is_repeatable():
    rows, _ = make_customers()
    first, second = train(rows), train(rows)
    assert [s.center for s in first.segments] == [s.center for s in second.segments]


def test_train_needs_enough_varied_customers():
    rows, _ = make_customers(per_group=4)
    assert train(rows[: model.MIN_CUSTOMERS - 1]) is None
    same = [Rfm(i, 10, 2, 500_000) for i in range(1, 30)]
    assert train(same) is None


def test_assign_new_customer_to_nearest_center():
    rows, _ = make_customers()
    trained = train(rows)
    newcomer, sleeper = Rfm(100, 3, 1, 250_000), Rfm(101, 400, 1, 400_000)
    assert [s.code for s in trained.assign([newcomer, sleeper])] == ["NEW", "LOST"]
    assert trained.assign([]) == []


def test_elbow_point_and_choose_k():
    scores = [
        KScore(1, 100.0, None),
        KScore(2, 40.0, None),
        KScore(3, 30.0, 0.45),
        KScore(4, 25.0, 0.41),
        KScore(5, 22.0, 0.28),
        KScore(6, 20.0, 0.31),
    ]
    assert elbow_point(scores) == 2
    # K lớn nhất từ điểm gãy trở lên mà Silhouette >= 0,3 và >= tốt nhất - 0,05.
    assert choose_k(scores, 2) == 4
    assert choose_k([*scores[:4], KScore(5, 22.0, 0.40)], 2) == 5
    assert choose_k(scores, 5) == 3  # từ điểm gãy không K nào đạt: Silhouette cao nhất
    # Không K nào đạt ngưỡng: lấy Silhouette cao nhất.
    low = [KScore(3, 30.0, 0.2), KScore(4, 25.0, 0.25), KScore(5, 22.0, 0.1)]
    assert choose_k(low, 3) == 4
    assert elbow_point(scores[:2]) == 1
    assert elbow_point([KScore(1, 5.0, None), KScore(2, 5.0, None), KScore(3, 5.0, None)]) == 1


def test_name_segments_gives_distinct_names():
    centers = np.array([[1.3, -0.8, -0.8], [1.2, -0.7, -0.9], [-1.0, 1.5, 1.5]])
    names = [t.code for t in name_segments(centers)]
    assert names[2] == "VIP"
    assert len(set(names)) == 3 and "LOST" in names[:2]


def test_helpers():
    assert format_money(1_249_600) == "1.250.000đ"
    now = naive(2026, 10, 28, 12)
    assert recency_days(naive(2026, 10, 26, 13), now) == 1
    assert recency_days(naive(2026, 10, 29, 0), now) == 0


def naive(*args: int) -> datetime:
    """Thời điểm không kèm múi giờ, như cột DATETIME của MySQL."""
    return datetime(*args, tzinfo=UTC).replace(tzinfo=None)
