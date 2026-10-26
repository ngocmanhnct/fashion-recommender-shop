"""Hàng đợi tính lại (BR-20): lấy các yêu cầu đến hạn trong bảng `recompute_request` và xử lý.

Backend ghi yêu cầu trong cùng giao dịch với hành động của khách; ml-service có đúng 1 tiến
trình lấy việc mỗi 2 giây (xem `worker.py`). Thiết kế: trinh-bay/cap-nhat-goi-y-va-phan-nhom.md.
"""
