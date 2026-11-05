# Ảnh minh họa theo danh mục

Bộ dữ liệu Tiki không có ảnh, và không dùng ảnh của Tiki. Sản phẩm chưa có ảnh riêng hiện ảnh minh
họa của danh mục: ảnh **CC0 hoặc public domain** lấy qua [Openverse](https://openverse.org), mỗi
danh mục 1 đến 5 ảnh (tối đa 8). Mỗi sản phẩm luôn hiện cùng 1 ảnh, chọn theo mã sản phẩm. Danh
mục chưa có ảnh vẫn hiện hình vẽ SVG như trước. Trang chi tiết sản phẩm ghi rõ "Ảnh minh họa theo
danh mục".

## Giấy phép

- Chỉ nhận [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) và
  [Public Domain Mark](https://creativecommons.org/publicdomain/mark/1.0/): dùng tự do, kể cả
  thương mại, không cần xin phép, không bắt buộc ghi nguồn. Ảnh lấy từ WordPress Photo Directory,
  rawpixel (bộ ảnh public domain), Flickr, Wikimedia Commons.
- `nguon-anh.md` ghi từng ảnh: tên ảnh kèm liên kết trang gốc, tác giả (nếu có), giấy phép, làm bằng
  chứng nguồn ảnh. Thông tin này lấy từ `anh-chon.json`.

## Chọn ảnh

- Ảnh chụp sản phẩm hoặc người đang dùng sản phẩm, sản phẩm nằm giữa khung (script cắt vuông ở giữa
  ảnh). Không lấy hình vẽ minh họa, tranh cổ, ảnh hiện vật bảo tàng khó nhận ra là hàng bán.
- Không thấy rõ logo, tên thương hiệu thật (Nike, Adidas, Gucci...): sản phẩm trong cửa hàng mang
  thương hiệu khác, ảnh có logo sẽ gây hiểu nhầm.
- Tránh ảnh có mặt người rõ nét; cạnh ngắn nên từ 640 px trở lên.

## Các bước (cmd, ở gốc repo)

`anh-chon.json` là danh sách ảnh đã chọn (đã commit), nên dựng lại chỉ cần bước 2 và 3.

1. Tìm ảnh ứng viên (chỉ khi muốn chọn lại hoặc thêm ảnh):

   ```bat
   uv run data/anh-san-pham/fetch_openverse.py search giay-de-xuong-nu
   ```

   Ảnh ứng viên vào `raw\_ung-vien\<thư mục>\` kèm `ung-vien.json`; kết quả tìm lưu ở
   `raw\_openverse-cache\` (API Openverse cho khách vô danh 20 lượt/phút, 200 lượt/ngày, thỉnh
   thoảng lỗi 500 với vài từ khóa: chạy lại sau). Từ khóa ở `QUERIES` trong script. Chép mục ảnh
   chọn được từ `ung-vien.json` sang `anh-chon.json` (theo thư mục danh mục).
2. Tải ảnh đã chọn vào `raw\<thư mục>\` (kèm `nguon.json`):

   ```bat
   uv run data/anh-san-pham/fetch_openverse.py download
   ```

   Wikimedia đôi khi trả 429 khi tạo ảnh thu nhỏ chưa có sẵn: chạy lại lệnh sau ít phút (ảnh đã tải
   được bỏ qua). Thư mục `raw/` nằm trong `.gitignore`: ảnh gốc không lên repo. Ảnh tải tay (không
   qua Openverse) cũng để được trong `raw\<thư mục>\`, `nguon-anh.md` khi đó chỉ ghi tên file.
3. Dựng ảnh:

   ```bat
   uv run data/anh-san-pham/build_photos.py
   ```

   Script in số danh mục có ảnh, dung lượng, danh mục còn thiếu, ảnh quá nhỏ và thư mục đặt sai tên.
   Chạy lại bao nhiêu lần cũng được.
4. Xem thử bằng `npm run dev` trong `frontend/`, rồi commit 3 thứ script sinh ra:
   `frontend/public/anh-danh-muc/`, `frontend/src/data/categoryPhotos.json`,
   `data/anh-san-pham/nguon-anh.md`. Không sửa tay 3 chỗ này.

## Từ khóa theo danh mục

| Danh mục | Thư mục trong `raw/` | Từ khóa (tìm tay trên kho ảnh khác) |
| --- | --- | --- |
| Balo du lịch | `balo-du-lich` | travel backpack |
| Balo laptop | `balo-laptop` | laptop backpack |
| Balo thời trang | `balo-thoi-trang` | fashion backpack, mini backpack |
| Cặp, túi chống sốc laptop | `cap-tui-chong-soc-laptop` | laptop sleeve, laptop bag |
| Dép nam | `dep-nam` | men slides, men slippers |
| Dép nữ | `dep-nu` | women slides, women slippers |
| Ghim cài áo | `ghim-cai-ao` | brooch, lapel pin |
| Giày boots nam | `giay-boots-nam` | men leather boots, chelsea boots |
| Giày boots nữ | `giay-boots-nu` | women ankle boots |
| Giày búp bê nữ | `giay-bup-be-nu` | ballet flats |
| Giày cao gót nữ | `giay-cao-got-nu` | high heels, stiletto |
| Giày lười nam | `giay-luoi-nam` | men loafers |
| Giày lười nữ | `giay-luoi-nu` | women loafers |
| Giày thể thao nam | `giay-the-thao-nam` | men sneakers |
| Giày thể thao nữ | `giay-the-thao-nu` | women sneakers |
| Giày tây nam | `giay-tay-nam` | men dress shoes, oxford shoes |
| Giày đế xuồng nữ | `giay-de-xuong-nu` | wedge shoes, wedge sandals |
| Găng tay | `gang-tay` | gloves, leather gloves |
| Gọng kính | `gong-kinh` | eyeglasses frames |
| Khăn | `khan` | scarf |
| Khẩu trang | `khau-trang` | cloth face mask |
| Kính mát | `kinh-mat` | sunglasses |
| Nón nam | `non-nam` | baseball cap, men hat |
| Nón nữ | `non-nu` | women sun hat, bucket hat |
| Phụ kiện du lịch | `phu-kien-du-lich` | travel neck pillow, luggage tag, travel accessories |
| Phụ kiện tóc | `phu-kien-toc` | hair clips, scrunchies |
| Phụ kiện, vệ sinh giày | `phu-kien-ve-sinh-giay` | shoe cleaning kit, shoe brush |
| Sandal nam | `sandal-nam` | men sandals |
| Sandal nữ | `sandal-nu` | women sandals |
| Thắt lưng nam | `that-lung-nam` | men leather belt |
| Thắt lưng nữ | `that-lung-nu` | women belt |
| Túi tote nữ | `tui-tote-nu` | tote bag |
| Túi xách nam | `tui-xach-nam` | men leather bag, briefcase |
| Túi xách nữ | `tui-xach-nu` | women handbag |
| Túi đeo chéo nam | `tui-deo-cheo-nam` | men crossbody bag, sling bag |
| Túi đeo chéo nữ | `tui-deo-cheo-nu` | women crossbody bag |
| Túi đựng đồ du lịch | `tui-dung-do-du-lich` | toiletry bag, packing cubes |
| Vali | `vali` | suitcase, luggage |
| Ví nam | `vi-nam` | men leather wallet |
| Ví nữ | `vi-nu` | women wallet, purse |
| Vớ nam | `vo-nam` | men socks |
| Vớ nữ | `vo-nu` | women socks |
