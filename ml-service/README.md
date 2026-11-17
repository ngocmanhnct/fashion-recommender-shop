# ml-service

Service FastAPI cho 2 tính năng ML của đề tài: gợi ý sản phẩm (hybrid: lọc theo nội dung, hay mua cùng, khảo sát, bán chạy, đang bán chạy) và phân nhóm khách hàng (RFM + K-Means). Chỉ được Spring Boot gọi nội bộ, không gọi thẳng từ trình duyệt. Từ Tuần 8 đọc/ghi MySQL: tiến trình lấy việc (`app/recompute/`) xử lý bảng `recompute_request` mỗi 2 giây (lượt lấy đủ 50 yêu cầu thì lấy tiếp ngay, không chờ).

## Chạy trên máy (cần uv: https://docs.astral.sh/uv/)

```powershell
uv sync                         # tạo .venv, cài đúng phiên bản theo uv.lock
uv run fastapi dev app/main.py  # http://localhost:8000/health, tài liệu API ở /docs
uv run pytest                   # chạy test (test CSDL cần MySQL, xem dưới)
uv run ruff check .             # kiểm tra lint
uv run ruff format .            # format code
```

## Kết nối MySQL

Dùng cùng biến môi trường với backend; mặc định khớp MySQL chạy bằng `docker compose up -d mysql` trên máy:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` | Máy chủ MySQL (trong Docker là `mysql`) |
| `DB_NAME` | `fashionshop` | CSDL của shop |
| `DB_USERNAME` / `MYSQL_ROOT_PASSWORD` | `root` / `changeme_local_dev` | Tài khoản MySQL |
| `RECOMPUTE_WORKER_ENABLED` | `true` | Bật tiến trình lấy việc |
| `RECOMPUTE_POLL_SECONDS` / `RECOMPUTE_BATCH_SIZE` | `2` / `50` | Nhịp lấy việc, số yêu cầu mỗi lượt |

`uv run` không đọc file `.env`: nạp `MYSQL_ROOT_PASSWORD` vào terminal trước (cmd: `set MYSQL_ROOT_PASSWORD=...`, PowerShell: `$env:MYSQL_ROOT_PASSWORD="..."`).

- `GET /recompute/status`: tiến trình lấy việc có chạy, có giữ khóa không, số yêu cầu đang chờ theo loại việc.
- Chỉ 1 tiến trình lấy việc trên 1 CSDL (khóa `GET_LOCK`). Chạy trên máy trong khi container ml-service cũng chạy thì bản sau chỉ đứng chờ.
- Test CSDL tạo lại `fashionshop_mltest` và chạy các migration của backend; không có MySQL thì các test đó được bỏ qua.

## Dữ liệu hành vi mô phỏng (Tuần 8)

Shop chưa có khách thật nên gợi ý và phân nhóm khách hàng dùng dữ liệu mô phỏng: khoảng 300 khách thuộc 7 kiểu (VIP, khách quen, tiết kiệm, mới, sắp rời bỏ, đã rời bỏ, chỉ xem) với lượt xem, thêm vào giỏ, đơn hàng 12 tháng gần nhất, đánh giá, khảo sát. Cần nạp danh mục sản phẩm mẫu trước (backend tự nạp lần đầu chạy).

```powershell
# cmd: set SIM_DEMO_PASSWORD=...   PowerShell: $env:SIM_DEMO_PASSWORD="..."
uv run python -m app.simulation              # xóa dữ liệu mô phỏng cũ rồi sinh lại (khoảng 10 giây)
uv run python -m app.simulation --reset-only # chỉ xóa
uv run python -m app.simulation --help       # số khách (--customers), seed (--seed)...
```

- Mọi tài khoản mô phỏng có email `@mophong.test`: xóa, sinh lại chỉ đụng các tài khoản này, không đụng tài khoản thật hay danh mục. Chạy trong 1 giao dịch, lỗi giữa chừng thì CSDL giữ nguyên.
- Cùng seed và cùng danh mục thì ra cùng dữ liệu (thời gian tính lùi từ lúc chạy). Không trừ tồn kho.
- 6 tài khoản demo, mỗi tài khoản cho thấy 1 trường hợp gợi ý/phân nhóm: `demo.vip`, `demo.tietkiem`, `demo.ngungmua`, `demo.chixem`, `demo.khaosat`, `demo.moi` (`@mophong.test`). Mật khẩu là biến môi trường `SIM_DEMO_PASSWORD` (ít nhất 8 ký tự), không ghi vào code hay git; không đặt thì tài khoản demo không đăng nhập được. Khách thường `kh0001@mophong.test`... không đăng nhập được.
- Sau khi ghi, mỗi khách có 1 yêu cầu tính lại gợi ý (và phân nhóm nếu có đơn đã giao) trong `recompute_request`; ml-service đang chạy sẽ tính gợi ý cho cả 300 khách trong khoảng 40 giây, yêu cầu phân nhóm nằm chờ đến Tuần 9.
- Trên staging: `docker compose exec -e SIM_DEMO_PASSWORD=... ml-service uv run --no-sync python -m app.simulation`.

Notebook EDA: `notebooks/eda_du_lieu_mo_phong.ipynb` (đã lưu kết quả chạy). Chạy lại sau khi sinh dữ liệu:

```powershell
uv sync --group eda
uv run --group eda jupyter execute --inplace notebooks/eda_du_lieu_mo_phong.ipynb
uv run ruff format notebooks
```

## Gợi ý sản phẩm (Tuần 8)

Package `app/recommendation/`. Mô hình dựng trong bộ nhớ từ MySQL (khoảng 0,3 giây với 1.050 sản phẩm), dựng lại khi sản phẩm, tồn kho, đơn hàng hay đánh giá đổi (kiểm tra mỗi 5 giây).

- **Vector sản phẩm** (`features.py`): TF-IDF tên + mô tả (từ đơn và cặp 2 từ), one-hot danh mục, thương hiệu, 5 khoảng giá; trọng số khối 0,45 / 0,30 / 0,15 / 0,10. Độ tương đồng cosine = tích vô hướng.
- **Hay mua cùng** (`copurchase.py`): cosine giữa 2 sản phẩm theo tập khách đã mua (đơn khác CANCELLED), co các cặp ít khách chung.
- **Đang bán chạy** (`trending.py`): số đã bán trong 30 ngày trước lúc gợi ý, mỗi lượt bán nhân 0,5^(tuổi tính bằng ngày / 7) nên lượt bán tuần trước nặng bằng nửa hôm nay; thang log chia cho sản phẩm cao nhất. Khi sản phẩm cao nhất chưa tới 20 lượt (đã giảm dần), điểm mọi sản phẩm thu nhỏ theo tỷ lệ, vì vài lượt bán lẻ ở cửa hàng ít đơn chỉ là ngẫu nhiên (dữ liệu mô phỏng khoảng 2–3 lượt, thu nhỏ còn khoảng 1/8). Tính tại lúc gợi ý nên mô hình không cần dựng lại khi thời gian trôi. Dữ liệu mô phỏng không có xu hướng theo thời gian, nên trọng số và chu kỳ bán rã chọn trên dữ liệu mua hàng thật của H&M (mục cuối).
- **Trang chủ** (`model.py`, nguồn theo BR-38): đủ dữ liệu hành vi (BR-19) thì trộn 0,7 × cosine với hồ sơ P(u) (đã mua 5, trong giỏ 3, yêu thích 2, đã xem 1, khảo sát 0,5) + 0,2 × hay mua cùng + 0,1 × bán chạy + 1 × đang bán chạy + 0,15 × điểm cộng theo kiểu người mua; chỉ có khảo sát thì cosine với vector khảo sát (khối chữ là TF-IDF của từ khóa gu và dịp đã chọn) + 0,2 × bán chạy + 0,15 × điểm cộng, xếp theo tầng: trong danh mục quan tâm và trong khoảng giá, trong danh mục nhưng ngoài khoảng giá, rồi mới tới danh mục khác; không có gì thì không có danh sách (trang chủ hiện bán chạy). Không gợi ý sản phẩm đã mua, đang trong giỏ, hết hàng; khảo sát chọn đồ nam/nữ thì không gợi ý đồ của giới tính kia (`gender.py`), trừ khi khách đã mua, thêm giỏ hoặc yêu thích đồ đó; trừ điểm sản phẩm trùng danh mục để danh sách đa dạng. Mỗi khách lưu 20 sản phẩm kèm lý do vào bảng `recommendation`.
- **Mô hình xếp hạng** (`ranker.py`, từ 17/11/2026): khi đã học được, mô hình này xếp gợi ý theo hành vi thay cho công thức ở trên. Mỗi khách lấy khoảng 250 sản phẩm ứng viên được phép gợi ý (100 đang bán chạy, 50 đang bán chạy trong nhóm tuổi của khách, 80 chia theo tỷ lệ các danh mục khách hay mua, 50 giống sản phẩm khách đã tương tác nhất, 50 hay mua cùng, 50 điểm công thức cao nhất), tính 28 đặc trưng (`FEATURES`: bán chạy theo chu kỳ bán rã 1, 3, 7, 14 ngày, tăng hay giảm so với tuần trước, số người mua, sản phẩm mới hay cũ, thứ hạng trong danh mục, giá, bán chạy trong nhóm tuổi của khách, tuổi và thói quen mua của khách, độ giống, hay mua cùng, điểm công thức, tỷ lệ danh mục, giá so với giá khách hay mua), rồi `HistGradientBoostingClassifier` của scikit-learn cho xác suất khách mua sản phẩm trong 7 ngày tới; gợi ý là các ứng viên xác suất cao nhất, lý do ghi như công thức. Học trên 5 mốc, mỗi mốc lùi 1 tuần: tại mỗi mốc chỉ dùng đơn trước mốc, nhãn là khách có mua sản phẩm đó trong 7 ngày sau mốc không (giữ mọi ứng viên khách mua, 30% ứng viên khách không mua). Học lại lúc khởi động và mỗi đêm (`ModelCache.train_ranker`), không học lại sau mỗi đơn; chưa đủ 5.000 lượt mua trúng ứng viên trong 5 tuần (`MIN_POSITIVES`) thì không dùng mô hình, gợi ý theo hành vi dùng công thức (điểm công thức vẫn là 1 đặc trưng khi có mô hình). Ngưỡng đo trên H&M (mục cuối): ít dữ liệu hơn thì mô hình học kém hoặc chỉ ngang công thức; dữ liệu mô phỏng 300 khách chỉ khoảng 100–250 lượt nên website demo dùng công thức. Tuổi lấy từ ngày sinh khách khai (`account.birthdate`), chia nhóm 5 tuổi (`trending.AGE_BAND_EDGES`); khách không khai thì đặc trưng nhóm tuổi tính trên mọi khách. Kiểm chứng trên H&M ở mục cuối.
- **Ưu tiên tương tác gần đây** (`model.py`): trọng số mỗi tương tác ở trên nhân thêm 1 + β × 0,5^(tuổi tính bằng ngày / bán rã), β = 3, bán rã 1 ngày: món vừa thêm giỏ hay vừa xem nặng gấp 4, sau 1 ngày gấp 2,5, sau 1 tuần gần như bình thường. Nhờ vậy thêm 1 món vào giỏ làm trung bình 1,6 trên 8 món trang chủ đổi (trước 0,5). Lý do ghi "bạn vừa thêm vào giỏ / vừa xem" khi tương tác trong 24 giờ.
- **Tính lại** (`store.py`): hàm xử lý yêu cầu RECOMMENDATION của tiến trình lấy việc (khoảng 20 ms mỗi khách). Lúc khởi động và 02:00 giờ Việt Nam mỗi đêm, ghi yêu cầu cho mọi khách có danh sách, có đơn Đã giao, có lượt xem 30 ngày hoặc đã làm khảo sát (BR-20).
- **API cho backend** (khoảng 5 ms): `GET /recommendations/related/{productId}?limit=4` (sản phẩm liên quan, US-12), `POST /recommendations/survey` (gợi ý theo khảo sát, US-23; body `categoryIds`, `brandId`, `priceMin`, `priceMax`, `style`, `minRating`, `gender`, `keywords`, `excludeProductIds`, `limit`), `POST /recommendations/bought-together` ("Thường được mua cùng" ở giỏ hàng, chức năng 20; body `productIds` 1–50 mã, `limit`; cộng cột ma trận hay mua cùng của các sản phẩm trong giỏ, chưa ai mua cùng thì rỗng). Xem `/docs`.

Đánh giá ngoại tuyến (giấu đơn cuối của mỗi khách rồi đo tỷ lệ trúng, precision, recall, NDCG ở 10 sản phẩm đầu; khởi động lạnh: đoán đơn đầu tiên chỉ từ khảo sát):

```powershell
uv run python -m app.recommendation.evaluate          # vài giây
uv run python -m app.recommendation.evaluate --grid   # thử thêm 243 bộ trọng số trộn (3 × 3 × 3 × 3 × 3) và 15 bộ β × bán rã; trọng số các khối vector chọn tay (vài phút)
```

Bảng kết quả có thêm dòng "Hybrid, không ưu tiên gần đây" (β = 0) và mốc "Xem gần đây (không mô hình)" để so. Lượt xem trong 48 giờ ngay trước đơn bị giấu bị bỏ (`SESSION_GAP`): khách xem đúng món sắp mua ngay trước khi đặt, giữ lại thì chỉ cần gợi ý lại món vừa xem đã trúng 100% (rò rỉ nhãn; trước 01/11/2026 bảng có lỗi này nên tỷ lệ trúng hybrid là 49%, nay khoảng 21–29% trên 3 bộ mô phỏng, vẫn hơn bán chạy 9–14% và xem gần đây 13–17%). Dòng "Hybrid cũ (chưa có đang bán chạy)" để so: trên dữ liệu mô phỏng, thêm đang bán chạy làm tỷ lệ trúng trung bình 3 bộ giảm nhẹ (26,0% → 25,0%, NDCG 0,097 → 0,095, trong khoảng dao động giữa các seed) vì dữ liệu này không có xu hướng; trên dữ liệu thật của H&M (mẫu 5%, tuần kiểm tra, đo 06/11/2026), thêm đang bán chạy làm công thức trúng 10,5% thay vì 4,8% (issue #143). "Hay mua cùng" và "bán chạy" vẫn dựng từ đơn của khách khác kể cả đơn sau thời điểm bị giấu, nên số vẫn hơi lạc quan. Ưu tiên gần đây không làm tăng tỷ lệ trúng ngoại tuyến; CTR trên trang chủ mới là số đo thật.

## Phân nhóm khách hàng (Tuần 9)

Package `app/segmentation/` (US-19, BR-23, BR-24). Chỉ khách có ít nhất 1 đơn Đã giao mới được phân nhóm.

- **R, F, M** (`rfm.py`): R = số ngày từ lúc đặt đơn Đã giao gần nhất, F = số đơn Đã giao, M = tổng `total_amount` các đơn Đã giao.
- **Huấn luyện** (`model.py`): đặc trưng ln(1 + R), ln(1 + F), ln(1 + M), chuẩn hóa z; K-Means với K = 1..8. Elbow (điểm gãy của inertia) và Silhouette: chọn K lớn nhất trong 3..6, không nhỏ hơn điểm gãy, Silhouette từ 0,3 và kém tốt nhất không quá 0,05. Cần ít nhất 20 khách. Tên nhóm (VIP, thân thiết, tiết kiệm, mới, có nguy cơ rời bỏ, đã lâu không mua) gán bằng cách ghép tâm nhóm với kiểu khách mẫu gần nhất (thuật toán Hungary, không trùng tên); mô tả ghi số liệu trung bình của nhóm và gợi ý chăm sóc.
- **Lưu** (`store.py`, migration V6): `segment_model` (tham số chuẩn hóa, K, Silhouette, mỗi lần huấn luyện 1 dòng), `customer_segment` (nhóm của lần mới nhất, tâm dạng z), `account_segment` (nhóm và R, F, M của từng khách).
- **Khi nào chạy**: yêu cầu SEGMENT_ASSIGN (backend ghi khi đơn sang Đã giao) gán khách vào nhóm có tâm gần nhất, không huấn luyện lại; chưa có mô hình thì huấn luyện lần đầu. Việc mỗi đêm huấn luyện lại khi mô hình đã đủ 1 tuần; chưa đủ thì gán lại mọi khách vào tâm gần nhất theo R, F, M mới nhất (R tăng mỗi ngày dù khách không mua, nên khách lâu không mua được chuyển nhóm ngay đêm đó).

Huấn luyện ngay và in bảng Elbow/Silhouette, các nhóm, so với kiểu khách mô phỏng (chỉ số ARI):

```powershell
uv run python -m app.segmentation.evaluate          # chỉ in, không ghi
uv run python -m app.segmentation.evaluate --save   # ghi mô hình vào CSDL (vd sau khi chạy lại mô phỏng)
uv run python -m app.segmentation.evaluate --features   # so các bộ đặc trưng ngoài R, F, M (không ghi)
```

**Đặc trưng ngoài R, F, M** (`features.py`, góp ý 30/10: thêm nếu có tác động): chạy cùng quy trình huấn luyện cho 9 bộ đặc trưng (thêm AOV = M/F, giá trung bình mỗi món, L = thời gian gắn bó của LRFM, khoảng cách trung bình giữa 2 đơn, số sản phẩm xem 90 ngày; hoặc thay M bằng AOV, giá mỗi món) rồi so ARI với kiểu khách mô phỏng. Trên 3 bộ dữ liệu mô phỏng (seed 2026, 2027, 2028) không bộ nào hơn R, F, M (ARI trung bình 0,46): LRFM 0,48 (chênh trong khoảng dao động giữa các seed), các bộ còn lại 0,36 – 0,47. Giá mỗi món của khách tiết kiệm thấp hơn khách quen (trung vị 78.000đ so với 134.000đ) nhưng dao động trong nhóm lớn, thêm vào chỉ làm nhiễu khoảng cách K-Means. Vì vậy mô hình giữ R, F, M.

## Kiểm chứng trên dữ liệu mua hàng thật (H&M)

`scripts/eval_hm.py` chạy nguyên mã gợi ý và phân nhóm ở trên với lịch sử mua thật của khách H&M (cuộc thi H&M Personalized Fashion Recommendations trên Kaggle). Gợi ý cắt theo thời gian như cách chấm của cuộc thi: mô hình chỉ thấy dữ liệu trước mốc, đoán sản phẩm mới khách mua trong 7 ngày sau, kèm khoảng tin cậy 95%. Cấu hình chọn trên các tuần kiểm định (đến 15/09/2020; báo cáo ghi lại tuần 09–15/09 kèm mức đóng góp của từng nhóm đặc trưng), tuần kiểm tra 16–22/09/2020 chỉ đo sau khi chốt. Ở mỗi mốc, mô hình xếp hạng học lại trên 5 tuần ngay trước mốc, như website học lại mỗi đêm. Phân nhóm kèm độ tuổi, độ ổn định và kiểm tra trên 90 ngày sau mốc. Luật cuộc thi cấm chia sẻ lại dữ liệu nên repo chỉ có kết quả tổng hợp (`data/hm/ket-qua.md` cho mẫu 5% khách, `ket-qua-toan-bo.md` cho toàn bộ khách, `ket-qua-giay-tui-phu-kien.md` cho riêng giày, túi, phụ kiện, `ket-qua-luong-du-lieu.md` cho ngưỡng dữ liệu của mô hình xếp hạng); cách tải dữ liệu và giới hạn ở `data/hm/README.md`.

Kết quả toàn bộ 1.362.281 khách (17/11/2026, tuần kiểm tra, 10.000 khách chọn ngẫu nhiên): mô hình xếp hạng trúng 15,7% (khoảng tin cậy 95%: 14,9–16,4%), hơn bán chạy 7 ngày trước mốc (10,3%; chênh +5,4 điểm, khoảng tin cậy +4,6 đến +6,1) và công thức hybrid (11,6%; chênh +4,0 điểm, +3,3 đến +4,7); từng nguồn riêng: đang bán chạy 9,7%, hay mua cùng 6,8%, bán chạy mọi thời điểm 4,4%, nội dung 1,3%. Tuần kiểm định: 14,6% so với 10,0% (bán chạy 7 ngày) và 10,8% (công thức). Xáo trộn từng nhóm đặc trưng ở tuần kiểm định: đang bán chạy theo thời gian làm tỷ lệ trúng giảm nhiều nhất (14,6% còn 5,7%), rồi hay mua cùng (còn 8,9%), sản phẩm mới hay cũ (10,1%), nhóm tuổi (12,1%); giá không đóng góp. Phân nhóm chọn K = 4 (Silhouette 0,375); khách VIP lúc mốc 25/06/2020 mua lại trong 90 ngày sau 79,4%, khách đã lâu không mua 10,2%.

Mẫu 5% khách (3.031 khách ở tuần kiểm tra): 13,2% (12,1–14,3%) so với bán chạy 7 ngày 9,6% (chênh +3,5 điểm, +2,3 đến +4,6) và công thức 10,5%. Mô hình học trên khoảng 3.300 khách mỗi tuần thay vì 10.000 nên hơn ít hơn so với toàn bộ khách.

Chỉ giày, túi, phụ kiện (`--products shoes-bags-accessories`; 587.605 khách, 7,4% lượt mua, mô hình xếp hạng học trên chính phần này): 14,5% (13,7–15,2%) so với bán chạy 7 ngày 12,9% (chênh +1,5 điểm, +0,8 đến +2,2), công thức 13,5% (+1,0 điểm, +0,3 đến +1,6) và riêng đang bán chạy 13,5% (+1,0 điểm, +0,2 đến +1,6). Phần này chỉ có 9.202 sản phẩm (mọi sản phẩm: 46.834) và bán chạy 7 ngày đã trúng 12,9%, nên mô hình hơn ít hơn. Phân nhóm vẫn chọn K = 4 (Silhouette 0,347; huấn luyện lại 5 lần, ARI 0,976); phân nhóm đến mốc 25/06/2020 (K = 3), khách VIP mua lại trong 90 ngày sau 33,3%, khách đã lâu không mua 10,5%.

Cần bao nhiêu dữ liệu (`--data-size`, căn cứ của `ranker.MIN_POSITIVES`): chia mẫu 5% thành các phần rời nhau như các cửa hàng nhỏ, mỗi phần tự học rồi so với công thức trên 3 tuần kiểm định. Khoảng 370 lượt mua trúng ứng viên trong 5 tuần học (mỗi phần 0,2% khách H&M): mô hình kém công thức 1,6 điểm (khoảng tin cậy −2,2 đến −1,1); khoảng 1.060 lượt: kém 1,2 điểm; khoảng 2.300 lượt: ngang (+0,1; −0,5 đến +0,6); khoảng 6.100 lượt: hơn 2,0 điểm (+1,3 đến +2,6). Vì vậy website chỉ dùng mô hình xếp hạng từ 5.000 lượt, dưới đó dùng công thức.

Trước khi có mô hình xếp hạng (06–07/11/2026), công thức hybrid chỉ hơn bán chạy 7 ngày 1,4 điểm trên toàn bộ khách, chưa phân biệt được trên mẫu 5% và trên giày, túi, phụ kiện; trước khi có điểm đang bán chạy (issue #143), công thức thua cả bán chạy 7 ngày. Cách chia "giữ lại đơn cuối của mỗi khách" bỏ khỏi script ngày 17/11/2026: mô hình xếp hạng học theo mốc thời gian chung cho mọi khách.

```powershell
uv run python -m scripts.eval_hm                 # mẫu 5% khách, khoảng 11 phút, RAM khoảng 1,6 GB
uv run python -m scripts.eval_hm --sample 1000 --stability-rounds 5   # toàn bộ khách, khoảng 75 phút, RAM khoảng 8,4 GB (data/hm/README.md)
uv run python -m scripts.eval_hm --sample 1000 --products shoes-bags-accessories --stability-rounds 5   # chỉ giày, túi, phụ kiện như website, ghi data/hm/ket-qua-giay-tui-phu-kien.md
uv run python -m scripts.eval_hm --data-size     # cần bao nhiêu dữ liệu (mẫu 5% khách), ghi data/hm/ket-qua-luong-du-lieu.md
```
