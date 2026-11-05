# Lịch sử dự án (project_history)

Xuất ngày 06/11/2026 từ GitHub REST API của repo `ngocmanhnct/fashion-recommender-shop`.

| File | Nội dung | Số dòng |
|---|---|---|
| `pr_history.json` | Mọi pull request: số, tiêu đề, trạng thái, người tạo, nhánh, lúc tạo, lúc merge, lúc đóng | 79 PR: 76 đã merge, 2 đóng không merge (#1 và #116 của Dependabot), 1 đang mở (#112, hồ sơ nộp) |
| `cicd_runs.json` | Mọi lần chạy GitHub Actions: workflow, sự kiện, nhánh, commit, kết quả | 190 lần: 179 lần workflow CI (162 thành công, 8 thất bại, 9 bị hủy) và 11 lần Dependabot |
| `bug_tracker.json` | Mọi issue có nhãn `bug`, kèm nội dung theo mẫu báo lỗi (mức độ, thành phần, cách tái hiện, nguyên nhân) | 56 issue, đã đóng 56 |

GitHub chỉ còn giữ các lần chạy từ 11/10/2026; các lần chạy CI sớm hơn (Tuần 4) không còn trên GitHub nên không có trong file.

## Pipeline CI/CD

Workflow `.github/workflows/ci.yml` chạy trên mỗi PR vào `main` và mỗi commit lên `main`, gồm 7 job (sơ đồ ở `SRS_SDD.MD` Mục 5.7):

| # | Job | Việc làm |
|---|---|---|
| 1 | Backend | Spotless, build, test JUnit trên MySQL 8.4 tạm, Checkstyle, JaCoCo |
| 2 | Frontend | Prettier, ESLint, Vitest kèm độ phủ, build |
| 3 | ML service | Ruff, pytest kèm độ phủ trên MySQL 8.4 tạm |
| 4 | Docker images | Build 3 image, chạy cả cụm bằng Docker Compose, test E2E Playwright |
| 5 | SonarQube Cloud | Phân tích tĩnh, nhận 3 báo cáo độ phủ (chờ job 1–3) |
| 6 | gitleaks | Quét secret trên toàn bộ lịch sử Git |
| 7 | Deploy staging | Chỉ với commit trên `main`, chờ job 1–4 và 6 xanh: build và đẩy image lên GHCR (gắn mã commit), SSH vào máy ảo Azure, `docker compose pull`, `up -d`, chờ health trả UP |

Workflow `rollback.yml` chạy tay để đưa staging về image của một commit cũ. Mọi thay đổi vào `main` đi qua PR và phải xanh các job bắt buộc.

Số lần deploy staging thành công từ 22/10 đến 06/11/2026: 48 (đếm job Deploy staging có kết quả success; chi tiết theo tuần ở `PROGRESS_LOG.MD`).

## Lỗi theo mức độ

Mức độ lấy từ trường "Mức độ" của mẫu báo lỗi. Một số issue mở trước khi có mẫu nên ghi theo lời (Cao, Trung bình, Thấp); bảng gộp các cách ghi gần nghĩa.

| Mức độ | Số issue |
|---|---:|
| Critical | 1 (#94, đã sửa, có test hồi quy) |
| Major hoặc Cao | 17 |
| Trung bình | 2 |
| Minor hoặc Thấp | 32 |
| Trivial | 3 |
| Không ghi | 1 |

Mỗi PR sửa lỗi ghi `Fixes #<số>` để issue tự đóng khi merge; vài PR thiếu dòng này nên issue được đóng tay (ghi trong `AI_LOG.MD`).

## Cách xuất lại

```bash
R=repos/ngocmanhnct/fashion-recommender-shop
gh api --paginate "$R/pulls?state=all&per_page=100" > pr_history.json
gh api --paginate "$R/issues?state=all&labels=bug&per_page=100" > bug_tracker.json
gh api "$R/actions/runs?per_page=100&page=1"   # lặp page=2, 3... đến hết total_count
```

Các file ở đây chỉ giữ các trường cần cho báo cáo (lệnh `jq` lọc trường).
