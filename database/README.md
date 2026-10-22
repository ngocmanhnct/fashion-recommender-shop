# database/: cấu trúc bảng và dữ liệu mẫu

Có 2 cách dựng lại cơ sở dữ liệu.

## Cách 1 (khuyên dùng): để backend tự dựng

Cấu trúc bảng được quản lý bằng Flyway, trong `backend/src/main/resources/db/migration/` (V1 đến V5, 25 bảng nghiệp vụ cộng bảng `flyway_schema_history`). Từ điển dữ liệu nằm trong `docs/dac-ta-yeu-cau.md`, Mục 3.

```powershell
docker compose up -d mysql
cd backend
.\mvnw.cmd spring-boot:run
```

Lần chạy đầu:
- Flyway tạo toàn bộ bảng.
- Backend nạp 1.050 sản phẩm mẫu từ `backend/src/main/resources/seed/tiki-catalog.json`. Việc này chỉ xảy ra khi bảng `product` đang trống, mất khoảng 15 giây.
- Backend tạo tài khoản admin theo `ADMIN_EMAIL` và `ADMIN_PASSWORD` trong `.env`.

## Cách 2: nạp từ file dump

- `schema.sql`: cấu trúc của toàn bộ bảng, không có dữ liệu.
- `seed-data.sql`: dữ liệu mẫu, gồm danh mục, thương hiệu, sản phẩm, biến thể, ảnh, câu hỏi khảo sát và lịch sử Flyway. **Không** chứa tài khoản, địa chỉ, đơn hàng hay dữ liệu cá nhân nào.

```powershell
docker compose up -d mysql
docker compose cp database/schema.sql mysql:/tmp/schema.sql
docker compose cp database/seed-data.sql mysql:/tmp/seed-data.sql
docker compose exec mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" fashionshop < /tmp/schema.sql && mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" fashionshop < /tmp/seed-data.sql'
```

### Cách tạo lại 2 file dump (chạy ở gốc repo, khi CSDL local vừa dựng xong theo Cách 1)

Lệnh dump và nạp đều chạy bên trong container, rồi chép file ra/vào, để PowerShell không đổi mã hóa (tiếng Việt) của file.

```powershell
docker compose exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --no-data --no-tablespaces --skip-comments fashionshop > /tmp/schema.sql'
docker compose exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --no-create-info --no-tablespaces --skip-comments fashionshop flyway_schema_history category brand product product_image product_variant survey_question survey_option survey_option_category > /tmp/seed-data.sql'
docker compose cp mysql:/tmp/schema.sql database/schema.sql
docker compose cp mysql:/tmp/seed-data.sql database/seed-data.sql
```
