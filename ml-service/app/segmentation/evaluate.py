"""Huấn luyện phân nhóm trên dữ liệu trong CSDL và in kết quả để đưa vào báo cáo.

In ra: bảng inertia (Elbow) và Silhouette theo K, K đã chọn, từng nhóm với tâm và số liệu trung
bình. Với khách mô phỏng (@mophong.test), so nhóm tìm được với kiểu khách thật lúc sinh dữ liệu
(bảng chéo và chỉ số ARI: 1 = khớp hoàn toàn, 0 = như chia ngẫu nhiên).

Chạy: `uv run python -m app.segmentation.evaluate` (trong ml-service/, cần MySQL như khi chạy
ml-service). Mặc định chỉ in, không ghi gì; thêm `--save` để ghi mô hình vào CSDL ngay thay vì
chờ lần huấn luyện hằng tuần (vd sau khi chạy lại dữ liệu mô phỏng); `--features` để so các bộ
đặc trưng ngoài R, F, M (module `features`, không ghi gì).
"""

import argparse
import re
import sys
from collections import Counter
from collections.abc import Sequence
from datetime import datetime

from sklearn.metrics import adjusted_rand_score
from sqlalchemy import Connection, Engine, text

from app.config import Settings
from app.db import create_db_engine
from app.recommendation.store import local_now
from app.segmentation import features
from app.segmentation.model import SegmentModel, format_money, train
from app.segmentation.rfm import Rfm, load_rfm
from app.segmentation.store import save_model
from app.simulation.personas import DEMO_ACCOUNTS, EMAIL_DOMAIN, assign_personas

DEFAULT_SEED = 2026
_CUSTOMER_EMAIL = re.compile(rf"^kh(\d+)@{re.escape(EMAIL_DOMAIN)}$")


def true_personas(conn: Connection, seed: int) -> dict[int, str]:
    """Kiểu khách thật của các tài khoản mô phỏng (theo mã tài khoản), dựng lại từ `seed`."""
    rows = conn.execute(
        text("SELECT id, email FROM account WHERE email LIKE :pattern"),
        {"pattern": f"%@{EMAIL_DOMAIN}"},
    ).all()
    numbered = {}
    for account_id, email in rows:
        match = _CUSTOMER_EMAIL.match(email)
        if match:
            numbered[int(account_id)] = int(match.group(1))
    labels = assign_personas(max(numbered.values(), default=0), seed)
    result = {account_id: labels[index - 1] for account_id, index in numbered.items()}
    demo = {d.email: d.persona for d in DEMO_ACCOUNTS}
    result.update({int(i): demo[e] for i, e in rows if e in demo})
    return result


def report(model: SegmentModel, rows: Sequence[Rfm], personas: dict[int, str]) -> str:
    """Kết quả huấn luyện dạng văn bản (bảng Markdown)."""
    lines = [
        f"Số khách có đơn Đã giao: {len(rows)}",
        "",
        "| K | Inertia | Silhouette |",
        "|---:|---:|---:|",
    ]
    for s in model.scores:
        silhouette = "" if s.silhouette is None else f"{s.silhouette:.3f}"
        lines.append(f"| {s.k} | {s.inertia:.1f} | {silhouette} |")
    lines += [
        "",
        (
            f"Điểm gãy (Elbow): K = {model.elbow_k}. Chọn K = {len(model.segments)}, "
            f"Silhouette = {model.silhouette:.3f}."
        ),
        "",
        "| Nhóm | Số khách | Tâm z (R, F, M) | R TB (ngày) | F TB (đơn) | M TB |",
        "|---|---:|---|---:|---:|---:|",
    ]
    for seg in model.segments:
        center = ", ".join(f"{c:+.2f}" for c in seg.center)
        lines.append(
            f"| {seg.name} | {seg.size} | {center} | {seg.mean_recency:.0f} | "
            f"{seg.mean_frequency:.1f} | {format_money(seg.mean_monetary)} |"
        )
    labelled = [(r, personas[r.account_id]) for r in rows if r.account_id in personas]
    if labelled:
        assigned = model.assign([r for r, _ in labelled])
        truth = [p for _, p in labelled]
        found = [s.code for s in assigned]
        codes = sorted(set(truth))
        counts = Counter(zip(found, truth, strict=True))
        lines += [
            "",
            (
                f"So với kiểu khách mô phỏng ({len(labelled)} khách): "
                f"ARI = {adjusted_rand_score(truth, found):.3f}"
            ),
            "",
            "| Nhóm \\ Kiểu thật | " + " | ".join(codes) + " |",
            "|---|" + "---:|" * len(codes),
        ]
        for seg in model.segments:
            cells = " | ".join(str(counts[(seg.code, c)]) for c in codes)
            lines.append(f"| {seg.name} | {cells} |")
    return "\n".join(lines)


def run(engine: Engine, seed: int, save: bool, now: datetime | None = None) -> str:
    """Huấn luyện, (tùy chọn) ghi vào CSDL và trả về báo cáo."""
    now = now or local_now()
    with engine.begin() as conn:
        rows = load_rfm(conn, now)
        model = train(rows)
        if model is None:
            return f"Chưa đủ dữ liệu để phân nhóm: {len(rows)} khách có đơn Đã giao."
        result = report(model, rows, true_personas(conn, seed))
        if save:
            save_model(conn, model, rows, now)
            result += "\n\nĐã ghi mô hình vào CSDL."
    return result


def run_features(engine: Engine, seed: int, now: datetime | None = None) -> str:
    """So các bộ đặc trưng (R, F, M và thêm đặc trưng khác) theo ARI với kiểu khách mô phỏng."""
    now = now or local_now()
    with engine.connect() as conn:
        rows = features.load_features(conn, now)
        personas = true_personas(conn, seed)
    results = features.compare(rows, personas)
    if not results:
        return f"Chưa đủ dữ liệu để so: {len(rows)} khách có đơn Đã giao."
    labelled = sum(1 for r in rows if r.account_id in personas)
    return f"So các bộ đặc trưng trên {labelled} khách mô phỏng:\n\n" + features.report(results)


def main(argv: list[str] | None = None, engine: Engine | None = None) -> int:
    """Điểm vào dòng lệnh."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--save", action="store_true", help="ghi mô hình vào CSDL")
    parser.add_argument(
        "--seed", type=int, default=DEFAULT_SEED, help="seed lúc sinh dữ liệu mô phỏng"
    )
    parser.add_argument(
        "--features", action="store_true", help="so các bộ đặc trưng ngoài R, F, M (không ghi gì)"
    )
    args = parser.parse_args(argv)
    engine = engine or create_db_engine(Settings.from_env())
    if args.features:
        print(run_features(engine, args.seed))
    else:
        print(run(engine, args.seed, args.save))
    return 0


if __name__ == "__main__":
    sys.exit(main())
