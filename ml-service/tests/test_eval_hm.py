"""Test script đánh giá trên dữ liệu H&M: đọc tệp (csv, zip), lấy mẫu, chuyển dữ liệu, báo cáo."""

import csv
import io
import random
import zipfile
from collections import Counter
from datetime import UTC, datetime, timedelta

import numpy as np
import pytest

from app.recommendation import ranker
from app.recommendation.features import ItemVectors
from scripts import eval_hm as ev
from tests import test_ranker as shop

TYPES = [
    (253, "Vest top", "Garment Upper body"),
    (272, "Sneakers", "Shoes"),
    (255, "T-shirt", "Garment Upper body"),
    (306, "Bag", "Accessories"),
]
WORDS = ["cotton", "jersey", "denim", "lace", "linen", "satin", "wool", "fleece"]
END = datetime(2020, 9, 22, tzinfo=UTC)
TRANSACTION_FIELDS = ["t_dat", "customer_id", "article_id", "price", "sales_channel_id"]


def customer_id(index: int) -> str:
    """Mã khách giả dạng mã băm hex 64 ký tự."""
    return f"{index:08x}" + "ab" * 28


def articles_rows() -> list[dict[str, str]]:
    """16 mẫu sản phẩm, mỗi mẫu 2 màu, 4 loại hàng (nửa là giày, túi)."""
    rows = []
    for code in range(1, 17):
        type_no, type_name, group = TYPES[code % 4]
        for colour in range(2):
            rows.append(
                {
                    "article_id": f"0{code * 1000 + colour}",
                    "product_code": str(code),
                    "prod_name": f"{WORDS[code % 8].title()} {type_name} {code}",
                    "product_type_no": str(type_no),
                    "product_type_name": type_name,
                    "product_group_name": group,
                    "colour_group_name": ["Black", "White"][colour],
                    "detail_desc": f"Soft {WORDS[code % 8]} {type_name.lower()} with "
                    f"{WORDS[(code + 3) % 8]} trim",
                }
            )
    rows.append({"article_id": "x", "product_code": "1", "product_type_no": "253"})
    return rows


def transaction_rows(customers: int = 60, seed: int = 7) -> list[dict[str, str]]:
    """Khách mua thường xuyên, vừa, đã bỏ đi; 2 nhóm sản phẩm hay mua cùng."""
    rng = random.Random(seed)
    rows = []
    for index in range(customers):
        kind = index % 3
        visits, last_day = ((8, 1), (4, 40), (2, 400))[kind]
        codes = list(range(1, 9)) if index % 2 == 0 else list(range(9, 17))
        for visit in range(visits):
            day = END - timedelta(days=last_day + (visits - 1 - visit) * rng.randint(15, 30))
            for code in rng.sample(codes, 2):
                rows.append(
                    {
                        "t_dat": f"{day:%Y-%m-%d}",
                        "customer_id": customer_id(index),
                        "article_id": f"0{code * 1000 + rng.randint(0, 1)}",
                        "price": f"{rng.uniform(0.01, 0.06):.6f}",
                        "sales_channel_id": str(1 + visit % 2),
                    }
                )
    bad = {"customer_id": customer_id(0), "sales_channel_id": "2"}
    rows += [
        {**bad, "t_dat": "2020-09-01", "article_id": "0999000", "price": "0.02"},
        {**bad, "t_dat": "ngày lạ", "article_id": "01000", "price": "0.02"},
        {**bad, "t_dat": "2020-09-01", "article_id": "01000", "price": "0"},
    ]
    return rows


def customer_rows(customers: int = 60) -> list[dict[str, str]]:
    """Tuổi của khách, 1/5 khách để trống."""
    return [
        {"customer_id": customer_id(i), "age": "" if i % 5 == 0 else str(18 + (i * 7) % 50)}
        for i in range(customers)
    ]


def to_csv(rows: list[dict[str, str]]) -> str:
    """Nội dung CSV có tiêu đề từ các cột của dòng đầu."""
    fields = list(dict.fromkeys(key for row in rows for key in row))
    buffer = io.StringIO()
    writer = csv.DictWriter(buffer, fields)
    writer.writeheader()
    writer.writerows(rows)
    return buffer.getvalue()


@pytest.fixture
def raw_dir(tmp_path):
    """Thư mục dữ liệu giả: articles.csv thường, giao dịch và khách nén zip như tải từ Kaggle."""
    raw = tmp_path / "raw"
    raw.mkdir()
    (raw / "articles.csv").write_text(to_csv(articles_rows()), encoding="utf-8")
    with zipfile.ZipFile(raw / "transactions_train.csv.zip", "w") as archive:
        archive.writestr("transactions_train.csv", to_csv(transaction_rows()))
    with zipfile.ZipFile(raw / "customers.zip", "w") as archive:
        archive.writestr("customers.csv", to_csv(customer_rows()))
    return raw


SMALL_RUN = ["--sample", "1000", "--rounds", "50", "--stability-rounds", "2", "--max-cases", "25"]


def test_report_end_to_end(raw_dir, tmp_path, monkeypatch):
    result = tmp_path / "ket-qua.md"
    monkeypatch.setattr(ev, "RAW_DIR", raw_dir)
    monkeypatch.setattr(ev, "RESULT_DIR", tmp_path)
    assert ev.main(SMALL_RUN) == 0
    report = result.read_text(encoding="utf-8")
    assert report.startswith("# Đánh giá trên dữ liệu mua hàng thật: H&M (Kaggle)\n")
    assert "Chỉ giữ nhóm hàng" not in report
    assert "Loại hàng nhiều lượt mua nhất" in report
    assert "| T-shirt |" in report and "| Sneakers |" in report
    assert "Mẫu 100,0% khách" in report
    assert "60 khách" in report
    assert "### Tuần kiểm định 08/09–14/09/2020" in report
    assert "### Tuần kiểm tra 15/09–21/09/2020 (kết quả)" in report
    # Dữ liệu giả quá ít để học mô hình xếp hạng: chỉ so công thức với bán chạy.
    assert "Mô hình xếp hạng: chưa đủ dữ liệu để học" in report
    assert ev.RANKER not in report
    assert f"| {ev.FORMULA} |" in report
    assert f"| Công thức hybrid − {ev.RECENT_POPULAR} |" in report
    # Từng nguồn riêng lẻ chỉ đo ở tuần kiểm tra.
    assert report.count("| Đang bán chạy |") == 1
    assert "Độ tuổi trong từng nhóm" in report
    assert "Độ ổn định" in report
    assert "Kiểm tra trên tương lai" in report
    assert "Bỏ 1 dòng: Sản phẩm không có trong articles.csv." in report
    assert "Bỏ 1 dòng: Ngày không đọc được." in report
    assert "Bỏ 1 dòng: Giá không dương." in report


def test_report_only_shoes_bags_accessories(raw_dir, tmp_path, monkeypatch):
    monkeypatch.setattr(ev, "RAW_DIR", raw_dir)
    monkeypatch.setattr(ev, "RESULT_DIR", tmp_path)
    # Hạ ngưỡng để mô hình xếp hạng học được trên dữ liệu giả rất nhỏ.
    monkeypatch.setattr(ranker, "MIN_POSITIVES", 1)
    assert ev.main([*SMALL_RUN, "--products", "shoes-bags-accessories"]) == 0
    # Ghi tệp riêng, không đè kết quả của mọi sản phẩm.
    assert not (tmp_path / "ket-qua.md").exists()
    report = (tmp_path / "ket-qua-giay-tui-phu-kien.md").read_text(encoding="utf-8")
    assert report.startswith(
        "# Đánh giá trên dữ liệu mua hàng thật: H&M (Kaggle), chỉ giày, túi, phụ kiện\n"
    )
    assert "Chỉ giữ nhóm hàng Accessories, Bags, Shoes của H&M (giày, túi, phụ kiện" in report
    assert "| Sneakers |" in report and "| Bag |" in report
    assert "T-shirt" not in report and "Vest top" not in report
    assert "mô hình xếp hạng học trên chính phần này" in report
    assert f"| {ev.RANKER} |" in report
    assert f"| Mô hình xếp hạng − {ev.FORMULA} |" in report
    assert "Mô hình xếp hạng học trên 5 tuần" in report
    assert "Kiểm tra trên tương lai" in report


def test_build_shop_keeps_only_chosen_groups():
    articles = ev.load_articles(articles_rows())
    assert (articles[1000].group, articles[1000].type_name) == ("Shoes", "Sneakers")
    rows = [
        {
            "t_dat": "2020-01-02",
            "customer_id": customer_id(1),
            "article_id": article_id,
            "price": "0.02",
            "sales_channel_id": "1",
        }
        for article_id in ("01000", "02000", "03000", "03001")
    ]
    shop = ev.build_shop(rows, articles, 1000, frozenset({"Shoes", "Accessories"}))
    # Mẫu 2 (T-shirt) ngoài nhóm hàng; 2 màu của mẫu 3 (Bag) gộp thành 1 sản phẩm.
    assert shop.outside == 1 and shop.lines == 3
    assert [i.id for i in shop.items] == [1, 3]
    assert shop.types == Counter({"Bag": 2, "Sneakers": 1})
    assert ev.build_shop(rows, articles, 1000).outside == 0


def test_build_shop_maps_orders_and_products():
    articles = ev.load_articles(articles_rows())
    assert len(articles) == 32  # dòng mã hỏng bị bỏ
    rows = [
        {
            "t_dat": "2020-01-02",
            "customer_id": customer_id(1),
            "article_id": "01000",
            "price": "0.02",
            "sales_channel_id": "2",
        },
        {
            "t_dat": "2020-01-02",
            "customer_id": customer_id(1),
            "article_id": "01001",
            "price": "0.04",
            "sales_channel_id": "2",
        },
        {
            "t_dat": "2020-01-02",
            "customer_id": customer_id(1),
            "article_id": "02000",
            "price": "0.03",
            "sales_channel_id": "1",
        },
        {
            "t_dat": "2020-01-01",
            "customer_id": customer_id(999),
            "article_id": "02000",
            "price": "0.03",
            "sales_channel_id": "1",
        },
    ]
    shop = ev.build_shop(rows, articles, per_mille=500)
    # Khách 999 ngoài mẫu 50%; 2 màu của mẫu 1 gộp thành 1 sản phẩm; 2 kênh = 2 đơn.
    assert len(shop.accounts) == 1
    assert [o.products for o in shop.orders] == [{1: 2}, {2: 1}]
    assert shop.totals[shop.orders[0].id] == 60_000
    assert [(i.id, i.category_id, i.price) for i in shop.items] == [
        (1, 272, 30_000),
        (2, 255, 30_000),
    ]
    assert shop.lines == 3


def test_time_split_only_sees_the_past():
    articles = ev.load_articles(articles_rows())
    rows = [
        {
            "t_dat": day,
            "customer_id": customer_id(1),
            "article_id": art,
            "price": "0.02",
            "sales_channel_id": "1",
        }
        for day, art in (("2020-09-01", "01000"), ("2020-09-20", "02000"), ("2020-09-21", "01000"))
    ]
    rows.append(
        {
            "t_dat": "2020-09-21",
            "customer_id": customer_id(2),
            "article_id": "03000",
            "price": "0.02",
            "sales_channel_id": "1",
        }
    )
    shop = ev.build_shop(rows, articles, per_mille=1000)
    cutoff = datetime(2020, 9, 16, tzinfo=UTC)
    # Khách 2 chưa mua gì trước mốc nên không có ca; khách 1 mua lại sản phẩm 1 không tính.
    (case,) = ranker.cases_at(shop.orders, cutoff, cutoff + timedelta(days=7))
    assert case.signals.purchased == {1}
    assert case.targets == {2}
    assert case.signals.now == cutoff
    model = ranker.model_at(ItemVectors(shop.items), shop.items, shop.orders, cutoff)
    assert model.snapshot(cutoff).sold_all.tolist() == [1, 0, 0]
    assert ev.recent_popular(shop.orders, datetime(2020, 9, 22, tzinfo=UTC), 7) == [1, 2, 3]


def test_feature_importance_shuffles_each_group():
    # Cửa hàng giả của test mô hình xếp hạng: đủ dữ liệu để học.
    vectors, orders, ages = shop.make_world()
    learned = ranker.train(vectors, shop.ITEMS, orders, ages, shop.END)
    model = ranker.model_at(vectors, shop.ITEMS, orders, shop.END, ranker.age_bands(ages))
    cases = ranker.cases_at(orders, shop.END, shop.END + timedelta(days=7), ages)
    lines = ev.feature_importance(model, learned, cases, 50)
    assert lines[0].startswith("Đủ đặc trưng: tỷ lệ trúng ")
    assert len(lines) == 4 + len(ranker.FEATURE_GROUPS)
    assert sum(line.startswith("| Nhóm tuổi |") for line in lines) == 1
    assert ev.feature_importance(model, learned, [], 50) == ["Không có ứng viên nào để đo."]


def test_top_hits_takes_k_best_candidates_per_customer():
    # Khách 0: món mua đứng thứ K + 1 (trượt); khách 1: món mua có điểm cao nhất (trúng).
    groups = np.array([0] * (ev.K + 1) + [1, 1])
    labels = np.array([0] * ev.K + [1] + [0, 1])
    score = np.array([float(ev.K + 1 - i) for i in range(ev.K + 1)] + [0.1, 0.9])
    assert ev._top_hits(groups, labels, score, 3).tolist() == [0.0, 1.0, 0.0]


def test_open_csv_errors(tmp_path):
    with zipfile.ZipFile(tmp_path / "articles.zip", "w") as archive:
        archive.writestr("ghi-chu.txt", "không có csv")
    with pytest.raises(FileNotFoundError), ev.open_csv(tmp_path, "articles"):
        pass
    with pytest.raises(FileNotFoundError), ev.open_csv(tmp_path, "customers"):
        pass


def test_main_without_files(tmp_path, monkeypatch, capsys):
    monkeypatch.setattr(ev, "RAW_DIR", tmp_path / "khong-co")
    assert ev.main([]) == 1
    assert "Không thấy articles.csv" in capsys.readouterr().err


@pytest.mark.parametrize(
    "args",
    [
        ["--sample", "0"],
        ["--sample", "1001"],
        ["--rounds", "0"],
        ["--stability-rounds", "0"],
        ["--max-cases", "-1"],
    ],
)
def test_main_rejects_out_of_range_arguments(args, tmp_path, monkeypatch, capsys):
    # Thư mục không có tệp: nếu không bị chặn ở bước kiểm tra, main trả 1 chứ không đọc dữ liệu thật.
    monkeypatch.setattr(ev, "RAW_DIR", tmp_path / "khong-co")
    with pytest.raises(SystemExit) as exit_info:
        ev.main(args)
    assert exit_info.value.code == 2
    assert "phải từ" in capsys.readouterr().err


def test_helpers():
    assert ev.in_sample(customer_id(49), 50)
    assert not ev.in_sample(customer_id(50), 50)
    assert not ev.in_sample("không-phải-hex", 1000)
    assert ev._decimal(1234.56, 1) == "1.234,6"
    assert ev._signed(-0.0123) == "−1,2"
    assert ev._band(24) == "Dưới 25" and ev._band(70) == "Từ 55"
    low, high = ev.bootstrap_ci(np.array([1.0] * 30 + [0.0] * 70), 500)
    assert low < 0.3 < high
    assert ev.bootstrap_ci(np.array([]), 10) == (0.0, 0.0)
    assert ev.load_ages(customer_rows(10), {customer_id(1): 7, customer_id(5): 8}) == {7: 25}


def test_run_without_orders_and_too_few_customers():
    empty = ev.Shop([], [], {}, {})
    assert ev.run(empty, {}, 50, 10, 1, 10) == "Không có giao dịch nào của khách trong mẫu."
    articles = ev.load_articles(articles_rows())
    rows = [
        {
            "t_dat": "2020-01-02",
            "customer_id": customer_id(1),
            "article_id": "01000",
            "price": "0.02",
            "sales_channel_id": "1",
        }
    ]
    shop = ev.build_shop(rows, articles, per_mille=1000)
    end = datetime(2020, 1, 3, tzinfo=UTC)
    assert "Chưa đủ dữ liệu" in ev.evaluate_segmentation(shop, {}, end, end, 1)
