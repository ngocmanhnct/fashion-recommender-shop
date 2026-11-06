# Báo cáo chất lượng mã nguồn (quality_reports)

Chạy ngày 06/11/2026 trên nhánh `main` tại commit `c415b14`. Các bước giống job CI trong `.github/workflows/ci.yml`; CI chạy lại toàn bộ trên mỗi PR, nên các file ở đây là ảnh chụp một lần, không tự cập nhật.

## Tóm tắt

| Hạng mục | Công cụ | Kết quả | File |
|---|---|---|---|
| Quy ước code | văn bản | 3 ngôn ngữ (Java, React, Python) | `CODING_CONVENTION.MD` |
| Linter backend | Checkstyle 14.3.0 (Google Style, mức warning chặn build) | 0 lỗi trên 246 file | `checkstyle_report.xml` |
| Định dạng backend | Spotless (google-java-format) | Đạt | lệnh ở dưới |
| Linter frontend | ESLint 10 | 0 lỗi, 0 cảnh báo trên 195 file | `eslint_report.json` |
| Định dạng frontend | Prettier | Đạt, mọi file đúng định dạng | `prettier_report.txt` |
| Linter ml-service | Ruff | 0 lỗi | `ruff_report.json` |
| Định dạng ml-service | Ruff format | Đạt, 48 file | `ruff_format_report.txt` |
| Quét secret | gitleaks 8.30.1, toàn bộ lịch sử `main` | 0 secret bị lộ (107 commit được quét) | `gitleaks_report.json`, `gitleaks_log.txt` |
| Độ phủ backend | JaCoCo, 220 test JUnit | 93,4% dòng (3.979/4.261), 84,0% nhánh | `coverage_backend_jacoco.csv` |
| Độ phủ ml-service | pytest-cov, 159 test | 97,3% dòng (2.471/2.540), 90,6% nhánh | `coverage_ml_service.xml` |
| Độ phủ frontend | Vitest (v8), 409 test | 86,7% dòng (1.996/2.302), 82,4% nhánh | `coverage_frontend_summary.json` |

Ngưỡng của Khoa: linter 0 lỗi, gitleaks 0 secret, độ phủ từ 70%. Cả ba service đều đạt. SonarQube Cloud (cổng chất lượng "Sonar way") phân tích mỗi PR vào `main`; ảnh chụp kết quả do sinh viên tự lấy vì trang sonarcloud.io cần đăng nhập.

## Cách chạy lại

```bash
# Backend (trong backend/), không cần MySQL
sh ./mvnw -B spotless:check checkstyle:check        # Windows: .\mvnw.cmd ...
# Độ phủ backend cần MySQL đang chạy: sh ./mvnw -B verify  ->  target/site/jacoco/jacoco.csv

# Frontend (trong frontend/)
npx eslint . --format json --output-file eslint_report.json
npx prettier --check .
npx vitest run --coverage --coverage.reporter=json-summary   # coverage/coverage-summary.json

# ml-service (trong ml-service/), cần MySQL và biến MYSQL_ROOT_PASSWORD
uv run ruff check . --output-format json
uv run ruff format --check .
ML_TEST_REQUIRE_DB=1 uv run pytest                    # coverage.xml

# gitleaks (ở gốc repo, cần lịch sử Git đầy đủ, không shallow)
docker run --rm --network none -v "$PWD:/repo:ro" -v "$PWD/out:/out" \
  zricethezav/gitleaks:v8.30.1 git /repo --log-opts="origin/main" --redact \
  --report-format json --report-path /out/gitleaks_report.json
```

Đường dẫn tuyệt đối của máy chạy đã được bỏ khỏi các file báo cáo, chỉ còn đường dẫn tính từ gốc repo.
