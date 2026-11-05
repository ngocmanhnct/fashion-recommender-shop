"""Chấm kết quả thử nghiệm người dùng: tỷ lệ hoàn thành tác vụ và điểm SUS theo từng vòng.

Cách dùng: python cham_diem.py ket-qua.csv
File CSV theo mẫu ket-qua-mau.csv, mỗi người một dòng mỗi vòng. Cột T1..T6 là 1 (hoàn thành)
hoặc 0; TG1..TG6 là số giây (có thể để trống); SUS1..SUS10 là số từ 1 đến 5.
Kết quả in ra dạng bảng Markdown để dán vào UAT_USABILITY_REPORT.MD.
"""

import csv
import statistics
import sys

TASKS = [f"T{i}" for i in range(1, 7)]
SUS_ITEMS = [f"SUS{i}" for i in range(1, 11)]
COMPLETION_TARGET = 90.0
SUS_TARGET = 80.0


def sus_score(row):
    """Điểm SUS 0-100: câu lẻ lấy (điểm - 1), câu chẵn lấy (5 - điểm), cộng lại nhân 2,5."""
    total = 0
    for i, item in enumerate(SUS_ITEMS, start=1):
        value = int(row[item])
        if not 1 <= value <= 5:
            raise ValueError(f"{row['ma_nguoi_dung']}: {item} = {value}, phải từ 1 đến 5")
        total += value - 1 if i % 2 == 1 else 5 - value
    return total * 2.5


def completed(row, task):
    """Một ô tác vụ: 1 là hoàn thành, 0 là không."""
    value = row[task].strip()
    if value not in ("0", "1"):
        raise ValueError(f"{row['ma_nguoi_dung']}: {task} = '{value}', phải là 0 hoặc 1")
    return value == "1"


def fmt(number):
    """Số kiểu Việt Nam, 1 chữ số thập phân."""
    return f"{number:.1f}".replace(".", ",")


def summarize(rows):
    """Tính số liệu của một vòng."""
    done = {t: sum(completed(r, t) for r in rows) for t in TASKS}
    scores = [sus_score(r) for r in rows]
    times = {}
    for t in TASKS:
        values = [float(r[f"TG{t[1:]}"]) for r in rows if r.get(f"TG{t[1:]}", "").strip()]
        times[t] = statistics.median(values) if values else None
    return {
        "users": len(rows),
        "done": done,
        "rate": 100 * sum(done.values()) / (len(rows) * len(TASKS)),
        "scores": scores,
        "times": times,
    }


def main(path):
    """Đọc CSV, in bảng theo vòng và bảng so sánh."""
    with open(path, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    rounds = sorted({r["vong"] for r in rows})
    result = {v: summarize([r for r in rows if r["vong"] == v]) for v in rounds}

    for v in rounds:
        s = result[v]
        print(f"## Vòng {v}: {s['users']} người\n")
        print("| Tác vụ | Hoàn thành | Tỷ lệ | Thời gian trung vị (giây) |")
        print("|---|---|---|---|")
        for t in TASKS:
            median = s["times"][t]
            shown = "" if median is None else fmt(median)
            print(
                f"| {t} | {s['done'][t]}/{s['users']} | {fmt(100 * s['done'][t] / s['users'])}% | {shown} |"
            )
        sc = s["scores"]
        print(f"\nTỷ lệ hoàn thành chung: {fmt(s['rate'])}%")
        print(
            f"SUS: trung bình {fmt(statistics.mean(sc))}, trung vị {fmt(statistics.median(sc))}, "
            f"thấp nhất {fmt(min(sc))}, cao nhất {fmt(max(sc))}"
        )
        users = [r for r in rows if r["vong"] == v]
        print("\n| Người dùng | SUS |\n|---|---|")
        for r, score in zip(users, sc):
            print(f"| {r['ma_nguoi_dung']} | {fmt(score)} |")
        print()

    print("## So sánh các vòng\n")
    print("| Chỉ số | " + " | ".join(f"Vòng {v}" for v in rounds) + " | Ngưỡng |")
    print("|---|" + "---|" * len(rounds) + "---|")
    print(
        "| Tỷ lệ hoàn thành tác vụ | "
        + " | ".join(f"{fmt(result[v]['rate'])}%" for v in rounds)
        + f" | ≥ {fmt(COMPLETION_TARGET)}% |"
    )
    print(
        "| Điểm SUS trung bình | "
        + " | ".join(fmt(statistics.mean(result[v]["scores"])) for v in rounds)
        + f" | ≥ {fmt(SUS_TARGET)} |"
    )


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Cách dùng: python cham_diem.py ket-qua.csv")
    main(sys.argv[1])
