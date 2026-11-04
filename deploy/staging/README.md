# Staging: sao lưu, khôi phục, quay về bản cũ

Cấu hình trong thư mục này chạy trên máy ảo Azure (`~/fashionshop/`). Job CI `Deploy staging`
chép `docker-compose.yml`, `Caddyfile`, `sao-luu.sh` sang máy ảo sau mỗi lần merge vào `main`.
Các lệnh dưới đây chạy trên máy ảo, trong thư mục `~/fashionshop`.

## 1. Sao lưu CSDL

Container `backup` (script `sao-luu.sh`) tự sao lưu:

- Mỗi ngày lúc 02:30 giờ Việt Nam, dump CSDL `fashionshop` thành
  `~/fashionshop/backup/fashionshop-<ngày>-<giờ>.sql.gz`.
- Khởi động mà chưa có bản nào trong 24 giờ qua thì dump ngay (máy ảo hay tắt lúc 02:30).
- Giữ 7 bản mới nhất, xóa bản cũ hơn. Đổi bằng `BACKUP_AT`, `BACKUP_KEEP_DAYS` trong `docker-compose.yml`.
- Dump dùng `--single-transaction`: không khóa ghi, web vẫn chạy trong lúc sao lưu.

```bash
ls -lh backup/                                 # các bản đã có
docker compose logs backup                     # giờ sao lưu, lỗi nếu có
docker compose exec backup bash /sao-luu.sh now   # sao lưu ngay (vd trước khi demo)
```

Bản sao lưu nằm trên cùng máy ảo: mất máy ảo là mất luôn. Trước buổi demo hay bảo vệ, chép 1 bản
về máy mình (chạy trên máy Windows, cmd):

```bat
scp -i "%USERPROFILE%\.ssh\fashionshop-staging_key.pem" azureuser@<IP>:fashionshop/backup/<tên file>.sql.gz .
```

## 2. Khôi phục CSDL từ bản sao lưu

Khôi phục **ghi đè toàn bộ** dữ liệu hiện tại (đơn hàng, tài khoản... sau thời điểm sao lưu sẽ mất).

```bash
cd ~/fashionshop
FILE=backup/fashionshop-20261105-023000.sql.gz      # chọn bản cần khôi phục

docker compose exec backup bash /sao-luu.sh now     # 1. sao lưu bản hiện tại phòng khi cần quay lại
docker compose stop backend ml-service              # 2. dừng 2 service đang ghi CSDL

# 3. Xóa CSDL cũ, tạo lại rỗng rồi nạp bản sao lưu (mật khẩu đọc từ biến trong container)
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE fashionshop; CREATE DATABASE fashionshop"'
gunzip -c "$FILE" | docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fashionshop'

docker compose start ml-service backend             # 4. chạy lại; Flyway thấy lược đồ đúng phiên bản
curl -s https://<tên miền staging>/actuator/health  # 5. phải ra "UP"
```

Đã thử trên máy dev (04/11/2026): dump CSDL mô phỏng (880 KB nén), nạp vào CSDL mới, số bảng
và số dòng của `orders`, `account`, `product_event_log` khớp bản gốc.

## 3. Quay staging về bản cũ (rollback)

Khi bản vừa merge làm staging lỗi:

1. Mở tab **Actions** của repo, chọn workflow **Rollback staging**, bấm **Run workflow** (nhánh `main`).
2. Nhập mã commit của bản chạy tốt trước đó (7 ký tự đầu là đủ). Lấy ở lịch sử job
   `Deploy staging` hoặc `git log --oneline main`.
3. Workflow kiểm tra commit nằm trên `main` và image của nó còn trên GHCR, rồi chạy lại đúng các
   image đó trên máy ảo (không build lại), chờ `/actuator/health` trả `UP`.

Sau đó sửa lỗi bằng PR như thường; lần merge kế tiếp tự deploy bản mới nhất.

**CSDL khi rollback:** không có bước hạ migration. Flyway của bản cũ bỏ qua các migration mới hơn
nó, nên quay về được khi các migration sau đó chỉ thêm bảng, hoặc thêm cột cho phép NULL hay có
giá trị mặc định. Nếu có migration đổi tên, xóa cột, hay thêm cột bắt buộc mà code cũ không ghi,
rollback xong phải khôi phục luôn bản sao lưu CSDL trước lần deploy lỗi (mục 2).

Cách thủ công, không qua GitHub (trên máy ảo):

```bash
cd ~/fashionshop && export IMAGE_TAG=<mã commit đầy đủ 40 ký tự>
docker compose pull -q && docker compose up -d
```

(Máy ảo cần đăng nhập GHCR trước: `docker login ghcr.io` bằng token có quyền `read:packages`.)
