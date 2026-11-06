# Dữ liệu mua hàng thật: H&M (Kaggle)

Dùng để kiểm chứng mô hình gợi ý và phân nhóm khách hàng của ml-service trên hành vi mua thật.
Dữ liệu mô phỏng (`ml-service/app/simulation`) vẫn giữ để demo và để đo ARI (chỉ mô phỏng mới biết
trước kiểu thật của từng khách). Bộ H&M **không** nạp vào cửa hàng: cửa hàng luôn học từ đơn hàng
của chính nó, tính lại mỗi đêm.

- Nguồn: cuộc thi "H&M Personalized Fashion Recommendations" của H&M Group trên Kaggle (2022),
  <https://www.kaggle.com/competitions/h-and-m-personalized-fashion-recommendations>.
- Điều kiện dùng (luật cuộc thi, mục 7): 7A chỉ cho dùng phi thương mại, cho nghiên cứu học thuật
  và giáo dục; 7B không được truyền, sao chép, công bố hay chia sẻ lại dữ liệu cho người chưa đồng
  ý luật. Vì vậy repo **không** chứa tệp gốc hay tệp đã lọc (`data/hm/raw/`, `data/hm/mau/` nằm
  trong `.gitignore`), chỉ có kết quả tổng hợp `ket-qua.md`.
- Nội dung: lịch sử mua thật của khách H&M (cửa hàng và trực tuyến) từ 20/09/2018 đến 22/09/2020;
  `articles.csv` có loại hàng, màu, mô tả của từng sản phẩm; `customers.csv` có tuổi khách. Mã khách
  đã được băm, giá đã được quy đổi về thang riêng (không phải tiền thật).
- Cỡ bộ đầy đủ: 31.788.324 dòng giao dịch, 1.371.980 khách, 105.542 sản phẩm-màu. Mẫu 5% khách dùng
  để đánh giá: 68.570 khách trong `customers.csv`, 1.574.807 dòng giao dịch (5,0%).

## Chạy lại

1. Tạo tài khoản Kaggle, mở trang cuộc thi, đồng ý luật và chờ Kaggle xác minh tài khoản.
2. Ở tab Data, tải riêng 3 tệp `transactions_train.csv` (khoảng 3,5 GB), `articles.csv`,
   `customers.csv`. Không cần thư mục `images` (khoảng 30 GB), đừng bấm "Download All". Chép vào
   `data/hm/raw/` (để nguyên tệp `.zip` cũng được).
3. Trong `ml-service/`:

   ```powershell
   uv run python -m scripts.eval_hm
   ```

   Script ghi `data/hm/ket-qua.md`. Đọc hết tệp giao dịch mất vài phút, phần đánh giá thêm khoảng
   10–15 phút. Cách ánh xạ sang cửa hàng, cách lấy mẫu 5% khách, 2 cách chia và các phép kiểm tra
   phân nhóm ghi ở đầu `ml-service/scripts/eval_hm.py`.

## Giới hạn

- Giá đã được quy đổi nên M (tổng chi) chỉ so sánh tương đối giữa các khách.
- Không có mã hóa đơn: 1 khách, 1 ngày, 1 kênh bán được coi là 1 đơn.
- Không có lượt xem, giỏ hàng, khảo sát: phần gợi ý cho khách mới (khởi động lạnh bằng khảo sát)
  vẫn chỉ đánh giá được trên dữ liệu mô phỏng. Cả bộ là 1 hãng nên không có khối thương hiệu.
- Sản phẩm là quần áo của H&M, cửa hàng của dự án bán giày, túi, phụ kiện. Kết quả cho thấy thuật
  toán chạy được trên hành vi mua thật; áp dụng cho cửa hàng thật thì hệ thống tự học lại trên dữ
  liệu của cửa hàng đó.
- Mô hình không dùng ảnh sản phẩm (cửa hàng chỉ có ảnh minh họa theo danh mục). Thêm vector ảnh vào
  phần gợi ý theo nội dung là hướng mở rộng khi có ảnh thật của từng sản phẩm.
