"""Module phân nhóm khách hàng (US-19, BR-23, BR-24): RFM + K-Means.

- `rfm`: đọc R, F, M của khách từ các đơn Đã giao.
- `model`: huấn luyện K-Means (chọn K theo Elbow + Silhouette), đặt tên nhóm, gán khách vào nhóm.
- `store`: ghi và đọc mô hình trong CSDL (segment_model, customer_segment, account_segment), hàm
  xử lý yêu cầu SEGMENT_ASSIGN và việc huấn luyện lại hằng tuần.
- `evaluate`: huấn luyện ngay và in kết quả (`uv run python -m app.segmentation.evaluate`).
"""
