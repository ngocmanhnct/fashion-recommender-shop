# Dữ liệu sản phẩm Tiki

Danh mục sản phẩm của cửa hàng lấy từ bộ **TIKI E-Commerce Fashion Products Dataset** trên Kaggle:
<https://www.kaggle.com/datasets/michaelminhpham/vietnamese-tiki-e-commerce-dataset>

- Giấy phép ghi trên trang Kaggle: *"Product crawled from open public API from TIKI. Dataset used
  for education purpose."* Chỉ dùng cho tiểu luận, không dùng thương mại, không dùng lại ảnh sản
  phẩm của Tiki (bộ dữ liệu cũng không có ảnh).
- 6 tệp CSV ngày 13/09/2023, tổng 41.603 dòng: giày nam, giày nữ, túi nam, túi nữ, balo – vali,
  phụ kiện thời trang. Không có quần áo, màu, size, tồn kho.

## Dựng lại file danh mục mẫu

1. Tải file `.zip` trên trang Kaggle (cần đăng nhập), chép vào `data/tiki/raw/` (không cần giải
   nén). Thư mục này nằm trong `.gitignore`: dữ liệu gốc không lên repo.
2. Trong `ml-service/`:

   ```powershell
   uv run python scripts/build_tiki_catalog.py
   ```

   Script ghi `backend/src/main/resources/seed/tiki-catalog.json` (1.050 sản phẩm, 42 danh mục).
   Cách chọn sản phẩm và phần dữ liệu sinh thêm (kích cỡ giày, tồn kho, mã SKU) ghi ở đầu script.
3. Backend tự nạp file này lúc khởi động nếu bảng `product` chưa có dòng nào
   (`CATALOG_SEED_ENABLED=false` để tắt). Muốn nạp lại từ đầu trên máy dev: `docker compose down -v`
   (xóa luôn dữ liệu MySQL), `docker compose up -d mysql`, rồi chạy lại backend.
