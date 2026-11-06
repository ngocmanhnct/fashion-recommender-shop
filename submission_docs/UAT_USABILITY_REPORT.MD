# BÁO CÁO THỰC NGHIỆM NGƯỜI DÙNG VÀ ĐIỂM SUS (UAT_USABILITY_REPORT.MD)

> Phục vụ TC2.7. Đề tài: Website bán hàng có tích hợp học máy trong gợi ý mua hàng và phân nhóm khách hàng. Sinh viên: Đoàn Ngọc Mạnh (23110124).

**Tình trạng ngày 06/11/2026: không thực nghiệm.** Sinh viên không tìm được người tham gia nên quyết định không làm thực nghiệm người dùng trong đề tài; luận văn ghi đây là hạn chế (mục 6.6) và hướng phát triển. File này giữ phương pháp, kịch bản và công cụ chấm để dùng khi làm sau. Các ô ghi *(chưa có số liệu)* chỉ được điền bằng kết quả thật sau buổi thử, không điền số ước đoán.

## 1. Mục tiêu và ngưỡng đạt

| Chỉ số | Cách đo | Ngưỡng (rubric TC2.7, KPI ở `SRS_SDD.MD` Mục 6) |
|---|---|---|
| Tỷ lệ hoàn thành tác vụ | Số lượt tác vụ hoàn thành ÷ (số người × 6 tác vụ) × 100% | ≥ 90% |
| Điểm SUS trung bình | Bảng hỏi SUS 10 câu, thang 0–100 | ≥ 80 |
| Số người tham gia | Người dùng thật, không phải thành viên dự án | ≥ 10 mỗi vòng |
| Vòng cải tiến | Sửa các lỗi tìm thấy ở vòng 1 rồi đo lại ở vòng 2 | ≥ 1 vòng |

## 2. Phương pháp

### 2.1 Người tham gia

Ít nhất 10 người cho mỗi vòng, không phải người làm dự án, có dùng trang mua sắm trực tuyến. Nên có cả người hiếm khi mua trực tuyến, và cả người dùng máy tính lẫn điện thoại. Ghi ẩn danh theo mã U01, U02...

Vòng 2 nên là người mới. Nếu không đủ người và phải mời lại người của vòng 1, ghi rõ ở Mục 4, vì người đã quen giao diện sẽ làm tốt hơn mà không nhờ phần sửa.

### 2.2 Cách tiến hành

1. Mỗi người làm riêng, khoảng 20 phút, trên trang staging `https://fashionshop-staging.eastasia.cloudapp.azure.com`.
2. Người quan sát đọc từng tác vụ trong `uat/kich-ban-tac-vu.md`, không chỉ cách làm, ghi hoàn thành hay không, thời gian, chỗ lúng túng.
3. Làm xong 6 tác vụ thì người tham gia điền phiếu SUS (`uat/phieu-sus.md`, in ra hoặc chuyển sang Google Form).
4. Nhập kết quả vào `uat/ket-qua.csv` theo mẫu `uat/ket-qua-mau.csv`, chạy `python uat/cham_diem.py uat/ket-qua.csv` và dán bảng in ra vào Mục 3.

### 2.3 Cách tính điểm SUS

Với mỗi người: câu lẻ (1, 3, 5, 7, 9) lấy điểm trừ 1; câu chẵn (2, 4, 6, 8, 10) lấy 5 trừ điểm; cộng 10 câu rồi nhân 2,5, được điểm từ 0 đến 100. Điểm của hệ thống là trung bình của mọi người trong vòng. Mốc tham khảo: 68 là mức trung bình của nhiều nghiên cứu SUS; từ 80 trở lên là tốt.

Tài liệu: J. Brooke, "SUS: A quick and dirty usability scale", trong *Usability Evaluation in Industry*, Taylor & Francis, 1996; J. Sauro, *A Practical Guide to the System Usability Scale*, Measuring Usability LLC, 2011 (mốc trung bình 68).

### 2.4 Sáu tác vụ

| # | Tác vụ | Chức năng |
|---|---|---|
| T1 | Tìm giày thể thao nam dưới 1 triệu, mở trang chi tiết | US-01, US-02, US-03 |
| T2 | Tạo tài khoản và làm khảo sát sở thích | US-04, US-23 |
| T3 | Chọn phân loại và thêm 2 sản phẩm vào giỏ | US-06, US-07 |
| T4 | Đặt hàng COD với địa chỉ mới và mã `THUNGHIEM` | US-08, US-09, US-10, US-21 |
| T5 | Tìm lại đơn vừa đặt và hủy | US-11, US-20 |
| T6 | Lưu một sản phẩm vào Yêu thích và mở danh sách | US-14 |

Tiêu chí hoàn thành và giới hạn thời gian từng tác vụ ở `uat/kich-ban-tac-vu.md`.

## 3. Kết quả vòng 1

Ngày thử: *(chưa có số liệu)*. Số người: *(chưa có số liệu)*.

### 3.1 Người tham gia (ẩn danh)

| Mã | Nhóm tuổi | Nghề nghiệp | Mua trực tuyến | Thiết bị |
|---|---|---|---|---|
| U01 | *(chưa có số liệu)* | | | |

### 3.2 Tỷ lệ hoàn thành và điểm SUS

*(dán bảng do `cham_diem.py` in ra)*

### 3.3 Vấn đề tìm thấy

| # | Vấn đề | Tác vụ | Số người gặp | Mức độ | Issue GitHub |
|---|---|---|---|---|---|
| 1 | *(chưa có số liệu)* | | | | |

Mỗi vấn đề cần sửa mở 1 issue nhãn `bug` (hoặc đề xuất cải tiến) và sửa qua PR như các lỗi khác.

## 4. Cải tiến và kết quả vòng 2

### 4.1 Đã sửa sau vòng 1

| Vấn đề | Cách sửa | PR |
|---|---|---|
| *(chưa có số liệu)* | | |

### 4.2 Kết quả vòng 2

Ngày thử: *(chưa có số liệu)*. Số người: *(chưa có số liệu)*. Người mới hay người của vòng 1: *(chưa có số liệu)*.

*(dán bảng do `cham_diem.py` in ra)*

## 5. So sánh trước và sau cải tiến

| Chỉ số | Vòng 1 | Vòng 2 | Ngưỡng | Đánh giá |
|---|---|---|---|---|
| Tỷ lệ hoàn thành tác vụ | *(chưa có số liệu)* | *(chưa có số liệu)* | ≥ 90% | |
| Điểm SUS trung bình | *(chưa có số liệu)* | *(chưa có số liệu)* | ≥ 80 | |

## 6. Kết luận và hạn chế

*(viết sau khi có số liệu: đạt hay chưa đạt từng ngưỡng, vấn đề còn lại, giới hạn của mẫu thử)*
