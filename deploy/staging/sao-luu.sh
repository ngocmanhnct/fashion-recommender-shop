#!/bin/bash
# Sao lưu CSDL staging (chạy trong container "backup" của docker-compose.yml, image mysql:8.4).
# - Mỗi ngày lúc BACKUP_AT (giờ Việt Nam) dump CSDL fashionshop thành backup/fashionshop-<ngày-giờ>.sql.gz
#   cạnh docker-compose.yml trên máy ảo; giữ BACKUP_KEEP_DAYS bản mới nhất, xóa bản cũ hơn.
# - Khởi động mà chưa có bản nào trong 24 giờ qua thì dump ngay (máy ảo hay bị tắt lúc 02:30).
# - Dump tay: docker compose exec backup bash /sao-luu.sh now
# --single-transaction đọc nhất quán các bảng InnoDB mà không khóa ghi, web vẫn chạy trong lúc dump.
# Khôi phục: mục 2 của deploy/staging/README.md.
set -euo pipefail

DIR=/backup
DB=fashionshop
KEEP=${BACKUP_KEEP_DAYS:-7}
AT=${BACKUP_AT:-02:30}

dump() {
  local file
  file="$DIR/$DB-$(date +%Y%m%d-%H%M%S).sql.gz"
  # Ghi ra file tạm rồi mới đổi tên: dump hỏng giữa chừng không để lại file trông như bản tốt.
  mysqldump -h mysql -uroot --single-transaction --routines --triggers --events \
    --set-gtid-purged=OFF --no-tablespaces "$DB" | gzip > "$file.tmp"
  mv "$file.tmp" "$file"
  echo "$(date '+%F %T') Đã sao lưu $(basename "$file") ($(du -h "$file" | cut -f1))"
  # Giữ KEEP bản mới nhất (đếm theo số bản, không theo ngày: máy ảo tắt nhiều ngày vẫn còn bản cũ).
  ls -1t "$DIR"/"$DB"-*.sql.gz | tail -n +"$((KEEP + 1))" | xargs -r rm -f
  rm -f "$DIR"/*.tmp
}

mkdir -p "$DIR"
if [ "${1:-}" = "now" ]; then
  dump
  exit 0
fi

# Mật khẩu đọc từ biến MYSQL_PWD (mysqldump tự dùng), không ghi trên dòng lệnh.
until mysqladmin -h mysql -uroot ping --silent >/dev/null 2>&1; do sleep 5; done

if [ -z "$(find "$DIR" -name "$DB-*.sql.gz" -mmin -1440 2>/dev/null)" ]; then
  dump || echo "$(date '+%F %T') Sao lưu lỗi, thử lại vào lần hẹn sau"
fi

while true; do
  next=$(date -d "today $AT" +%s)
  now=$(date +%s)
  [ "$next" -le "$now" ] && next=$(date -d "tomorrow $AT" +%s)
  echo "$(date '+%F %T') Lần sao lưu tiếp theo: $(date -d "@$next" '+%F %T')"
  sleep $((next - now))
  dump || echo "$(date '+%F %T') Sao lưu lỗi, thử lại vào lần hẹn sau"
done
