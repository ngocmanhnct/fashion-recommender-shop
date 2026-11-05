# Kịch bản tác vụ cho buổi thử nghiệm người dùng

Dùng cho `UAT_USABILITY_REPORT.MD`. Mỗi người làm 6 tác vụ trên trang staging, mất khoảng 20 phút, sau đó điền phiếu SUS (`phieu-sus.md`).

## Chuẩn bị trước buổi thử (sinh viên làm)

1. Bật máy ảo staging và kiểm tra `https://fashionshop-staging.eastasia.cloudapp.azure.com` mở được (Resume 2 monitor UptimeRobot).
2. Đăng nhập Quản trị viên, tạo mã giảm giá công khai `THUNGHIEM` ở trang Mã giảm giá: giảm 10%, đơn tối thiểu 0đ, đủ lượt cho mọi người tham gia, còn hạn qua ngày thử.
3. Chuẩn bị một máy tính hoặc điện thoại có trình duyệt, xóa dữ liệu trang staging (đăng xuất, xóa giỏ) trước mỗi người.
4. In `phieu-sus.md` và bảng ghi chép ở cuối file này, hoặc chuyển phiếu SUS sang Google Form.
5. Người tham gia tự đăng ký bằng email của họ hoặc một địa chỉ thử dạng `ten.ho+thunghiem@gmail.com`. Không dùng tài khoản `@mophong.test`.

## Lời dặn đọc cho người tham gia

"Bạn đang thử một trang web bán giày, túi, balo và phụ kiện. Mình kiểm tra trang web, không kiểm tra bạn. Hãy làm như đang tự mua sắm và nói to điều bạn đang nghĩ. Mình sẽ không chỉ cách làm trong lúc bạn làm; nếu bạn bí quá thì cứ nói 'mình bỏ qua'."

## 6 tác vụ

Ghi **Hoàn thành** khi người dùng làm xong, đúng tiêu chí, trong giới hạn thời gian và không cần người quan sát chỉ cách. Cần gợi ý, quá giờ hay bỏ qua đều ghi **Không hoàn thành**.

| # | Tác vụ (đọc cho người dùng) | Tiêu chí hoàn thành | Giới hạn | Chức năng được thử |
|---|---|---|---|---|
| T1 | "Bạn muốn mua một đôi giày thể thao nam giá dưới 1 triệu. Hãy tìm một đôi và mở trang chi tiết của nó." | Đang ở trang chi tiết của một sản phẩm giày nam có giá dưới 1.000.000đ | 3 phút | US-01, US-02, US-03 |
| T2 | "Hãy tạo tài khoản rồi làm bảng hỏi sở thích để trang web gợi ý sản phẩm cho bạn." | Tài khoản đã tạo, khảo sát làm xong, thấy danh sách gợi ý | 4 phút | US-04, US-23 |
| T3 | "Chọn một sản phẩm có nhiều lựa chọn (màu hoặc kích cỡ), chọn một lựa chọn và thêm 2 cái vào giỏ." | Giỏ có đúng sản phẩm, đúng lựa chọn, số lượng 2 | 3 phút | US-06, US-07 |
| T4 | "Đặt hàng giỏ hàng này, thanh toán khi nhận hàng, giao về một địa chỉ bất kỳ, và dùng mã giảm giá THUNGHIEM." | Đơn tạo thành công, có giảm giá, phương thức COD | 5 phút | US-08, US-09, US-10, US-21 |
| T5 | "Bạn đổi ý. Hãy tìm lại đơn vừa đặt và hủy nó." | Đơn chuyển sang Đã hủy | 2 phút | US-11, US-20 |
| T6 | "Hãy lưu một sản phẩm bạn thích để xem lại sau, rồi mở danh sách đã lưu." | Sản phẩm có trong trang Yêu thích | 2 phút | US-14 |

## Bảng ghi chép của người quan sát (mỗi người 1 bảng)

Mã người dùng: U__   Vòng: __   Ngày: __/__/2026   Thiết bị: máy tính / điện thoại

| # | Hoàn thành (1/0) | Thời gian (giây) | Số lần sai đường | Ghi chú: chỗ người dùng lúng túng, câu họ nói |
|---|---|---|---|---|
| T1 | | | | |
| T2 | | | | |
| T3 | | | | |
| T4 | | | | |
| T5 | | | | |
| T6 | | | | |

Nhập kết quả vào `ket-qua.csv` (mẫu `ket-qua-mau.csv`), mỗi người một dòng, rồi chạy `python cham_diem.py ket-qua.csv`.
