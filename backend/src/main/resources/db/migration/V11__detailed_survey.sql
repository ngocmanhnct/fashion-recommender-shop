-- V11__detailed_survey.sql
--
-- Khảo sát chi tiết hơn và sửa lỗi chọn đồ nữ mà gợi ý ra đồ nam (issue sửa lỗi giới tính).
-- Không sửa V5 vì Flyway kiểm tra checksum.
--
--   1. survey_question.category_mode: ADD (đáp án thêm 1 nhóm danh mục) hoặc NARROW (đáp án thu
--      hẹp các nhóm đã có: mỗi nhóm chỉ giữ danh mục hợp với dịp đã chọn, nhóm không có danh mục
--      nào hợp thì giữ nguyên; chưa có nhóm nào thì thêm như ADD). Câu dịp sử dụng Q2, G2 là NARROW.
--   2. survey_option.keywords: từ khóa mô tả gu và dịp, đưa vào khối chữ (TF-IDF) của vector sở
--      thích ở dịch vụ học máy.
--   3. survey_result.gender, survey_result.keywords: giới tính đồ cần tìm và từ khóa của lần làm
--      khảo sát, để gợi ý trang chủ không lẫn đồ của giới tính kia.
--   4. Câu hỏi mới: loại đồ cần tìm (N1 nam, F1 nữ; GN, GF khi tìm quà), gu thời trang (AM, AF),
--      dịp "Đi tiệc, sự kiện".
--
-- Đường đi mới (số câu): bản thân Q1-N1|F1-AM|AF-Q2-Q3 rồi tiết kiệm S1-S2 (7), thương hiệu
-- B1-B2-P1 (8), cầu toàn C1-P1 (7), xu hướng P1 (6); quà tặng Q1-G1-GN|GF-G2-G3 (5), người nhận
-- "không muốn nói" Q1-G1-G2-G3 (4). BR-41.

-- ============================================================
-- 1-3. Cột mới
-- ============================================================
ALTER TABLE survey_question
    ADD COLUMN category_mode ENUM('ADD','NARROW') NOT NULL DEFAULT 'ADD' AFTER max_choices;

ALTER TABLE survey_option
    ADD COLUMN keywords VARCHAR(255) NULL;

ALTER TABLE survey_result
    ADD COLUMN gender   ENUM('MALE','FEMALE') NULL AFTER status,
    ADD COLUMN keywords VARCHAR(500)          NULL AFTER min_rating;

-- Khảo sát đã làm trước V11: lấy giới tính từ câu trả lời Q1 đã lưu.
UPDATE survey_result
SET gender = CASE
        WHEN JSON_SEARCH(answers, 'one', 'SELF_FEMALE') IS NOT NULL THEN 'FEMALE'
        WHEN JSON_SEARCH(answers, 'one', 'SELF_MALE') IS NOT NULL THEN 'MALE'
    END
WHERE status = 'COMPLETED' AND answers IS NOT NULL;

UPDATE survey_question
SET category_mode = 'NARROW',
    hint = 'Chọn tối đa 3 dịp, loại đồ bạn chọn sẽ được lọc theo dịp'
WHERE code IN ('Q2', 'G2');

-- ============================================================
-- 4. Câu hỏi mới
-- ============================================================
INSERT INTO survey_question (code, content, hint, answer_type, max_choices) VALUES
    ('N1', 'Bạn đang cần món gì?', 'Chọn tối đa 3 loại', 'MULTIPLE', 3),
    ('F1', 'Bạn đang cần món gì?', 'Chọn tối đa 3 loại', 'MULTIPLE', 3),
    ('AM', 'Gu ăn mặc của bạn gần với kiểu nào nhất?', NULL, 'SINGLE', 1),
    ('AF', 'Gu ăn mặc của bạn gần với kiểu nào nhất?', NULL, 'SINGLE', 1),
    ('GN', 'Bạn định tặng món gì?', 'Chọn tối đa 3 loại', 'MULTIPLE', 3),
    ('GF', 'Bạn định tặng món gì?', 'Chọn tối đa 3 loại', 'MULTIPLE', 3);

-- N1, F1: loại đồ (mỗi đáp án là 1 nhóm danh mục). ANY = chưa biết, để câu dịp sử dụng chọn.
INSERT INTO survey_option (question_id, code, label, display_order)
SELECT q.id, v.code, v.label, v.ord
FROM survey_question q
JOIN (SELECT 'FORMAL' AS code, 'Giày tây, giày lười' AS label, 1 AS ord
      UNION ALL SELECT 'SNEAKER', 'Giày thể thao', 2
      UNION ALL SELECT 'BOOTS', 'Giày boots', 3
      UNION ALL SELECT 'SANDAL', 'Sandal, dép', 4
      UNION ALL SELECT 'BAG', 'Túi xách, túi đeo chéo, cặp laptop', 5
      UNION ALL SELECT 'BACKPACK', 'Balo, vali, đồ du lịch', 6
      UNION ALL SELECT 'WALLET_BELT', 'Ví, thắt lưng', 7
      UNION ALL SELECT 'ACCESSORY', 'Nón, kính, vớ và phụ kiện khác', 8
      UNION ALL SELECT 'ANY', 'Chưa biết, gợi ý giúp mình', 9) v
WHERE q.code = 'N1';

INSERT INTO survey_option (question_id, code, label, display_order)
SELECT q.id, v.code, v.label, v.ord
FROM survey_question q
JOIN (SELECT 'HEELS' AS code, 'Giày cao gót, búp bê, giày lười' AS label, 1 AS ord
      UNION ALL SELECT 'SNEAKER', 'Giày thể thao', 2
      UNION ALL SELECT 'BOOTS', 'Giày boots', 3
      UNION ALL SELECT 'SANDAL', 'Sandal, dép, giày đế xuồng', 4
      UNION ALL SELECT 'BAG', 'Túi xách, túi tote, túi đeo chéo', 5
      UNION ALL SELECT 'BACKPACK', 'Balo, vali, đồ du lịch', 6
      UNION ALL SELECT 'WALLET_BELT', 'Ví, thắt lưng', 7
      UNION ALL SELECT 'ACCESSORY', 'Nón, kính, phụ kiện tóc và phụ kiện khác', 8
      UNION ALL SELECT 'ANY', 'Chưa biết, gợi ý giúp mình', 9) v
WHERE q.code = 'F1';

INSERT INTO survey_option_category (option_id, category_name)
SELECT o.id, m.category_name
FROM survey_option o
JOIN survey_question q ON q.id = o.question_id AND q.code = 'N1'
JOIN (SELECT 'FORMAL' AS code, 'Giày tây nam' AS category_name
      UNION ALL SELECT 'FORMAL', 'Giày lười nam'
      UNION ALL SELECT 'SNEAKER', 'Giày thể thao nam'
      UNION ALL SELECT 'BOOTS', 'Giày boots nam'
      UNION ALL SELECT 'SANDAL', 'Sandal nam'
      UNION ALL SELECT 'SANDAL', 'Dép nam'
      UNION ALL SELECT 'BAG', 'Túi xách nam'
      UNION ALL SELECT 'BAG', 'Túi đeo chéo nam'
      UNION ALL SELECT 'BAG', 'Cặp, túi chống sốc laptop'
      UNION ALL SELECT 'BACKPACK', 'Balo laptop'
      UNION ALL SELECT 'BACKPACK', 'Balo thời trang'
      UNION ALL SELECT 'BACKPACK', 'Balo du lịch'
      UNION ALL SELECT 'BACKPACK', 'Vali'
      UNION ALL SELECT 'BACKPACK', 'Túi đựng đồ du lịch'
      UNION ALL SELECT 'BACKPACK', 'Phụ kiện du lịch'
      UNION ALL SELECT 'WALLET_BELT', 'Ví nam'
      UNION ALL SELECT 'WALLET_BELT', 'Thắt lưng nam'
      UNION ALL SELECT 'ACCESSORY', 'Nón nam'
      UNION ALL SELECT 'ACCESSORY', 'Kính mát'
      UNION ALL SELECT 'ACCESSORY', 'Gọng kính'
      UNION ALL SELECT 'ACCESSORY', 'Vớ nam'
      UNION ALL SELECT 'ACCESSORY', 'Găng tay'
      UNION ALL SELECT 'ACCESSORY', 'Khẩu trang'
      UNION ALL SELECT 'ACCESSORY', 'Khăn'
      UNION ALL SELECT 'ACCESSORY', 'Phụ kiện, vệ sinh giày') m ON m.code = o.code;

INSERT INTO survey_option_category (option_id, category_name)
SELECT o.id, m.category_name
FROM survey_option o
JOIN survey_question q ON q.id = o.question_id AND q.code = 'F1'
JOIN (SELECT 'HEELS' AS code, 'Giày cao gót nữ' AS category_name
      UNION ALL SELECT 'HEELS', 'Giày búp bê nữ'
      UNION ALL SELECT 'HEELS', 'Giày lười nữ'
      UNION ALL SELECT 'SNEAKER', 'Giày thể thao nữ'
      UNION ALL SELECT 'BOOTS', 'Giày boots nữ'
      UNION ALL SELECT 'SANDAL', 'Sandal nữ'
      UNION ALL SELECT 'SANDAL', 'Dép nữ'
      UNION ALL SELECT 'SANDAL', 'Giày đế xuồng nữ'
      UNION ALL SELECT 'BAG', 'Túi xách nữ'
      UNION ALL SELECT 'BAG', 'Túi tote nữ'
      UNION ALL SELECT 'BAG', 'Túi đeo chéo nữ'
      UNION ALL SELECT 'BAG', 'Cặp, túi chống sốc laptop'
      UNION ALL SELECT 'BACKPACK', 'Balo laptop'
      UNION ALL SELECT 'BACKPACK', 'Balo thời trang'
      UNION ALL SELECT 'BACKPACK', 'Balo du lịch'
      UNION ALL SELECT 'BACKPACK', 'Vali'
      UNION ALL SELECT 'BACKPACK', 'Túi đựng đồ du lịch'
      UNION ALL SELECT 'BACKPACK', 'Phụ kiện du lịch'
      UNION ALL SELECT 'WALLET_BELT', 'Ví nữ'
      UNION ALL SELECT 'WALLET_BELT', 'Thắt lưng nữ'
      UNION ALL SELECT 'ACCESSORY', 'Nón nữ'
      UNION ALL SELECT 'ACCESSORY', 'Kính mát'
      UNION ALL SELECT 'ACCESSORY', 'Gọng kính'
      UNION ALL SELECT 'ACCESSORY', 'Phụ kiện tóc'
      UNION ALL SELECT 'ACCESSORY', 'Ghim cài áo'
      UNION ALL SELECT 'ACCESSORY', 'Khăn'
      UNION ALL SELECT 'ACCESSORY', 'Vớ nữ'
      UNION ALL SELECT 'ACCESSORY', 'Khẩu trang'
      UNION ALL SELECT 'ACCESSORY', 'Găng tay') m ON m.code = o.code;

-- GN, GF: cùng đáp án và danh mục với N1, F1.
INSERT INTO survey_option (question_id, code, label, display_order)
SELECT g.id, o.code, o.label, o.display_order
FROM survey_question g
JOIN survey_question s ON s.code = CASE g.code WHEN 'GN' THEN 'N1' ELSE 'F1' END
JOIN survey_option o ON o.question_id = s.id
WHERE g.code IN ('GN', 'GF');

INSERT INTO survey_option_category (option_id, category_name)
SELECT go.id, c.category_name
FROM survey_question g
JOIN survey_option go ON go.question_id = g.id
JOIN survey_question s ON s.code = CASE g.code WHEN 'GN' THEN 'N1' ELSE 'F1' END
JOIN survey_option so ON so.question_id = s.id AND so.code = go.code
JOIN survey_option_category c ON c.option_id = so.id
WHERE g.code IN ('GN', 'GF');

-- AM, AF: gu thời trang, chỉ đóng góp từ khóa (các từ có trong tên, mô tả sản phẩm mẫu).
INSERT INTO survey_option (question_id, code, label, display_order, keywords)
SELECT q.id, v.code, v.label, v.ord, v.keywords
FROM survey_question q
JOIN (SELECT 'AM' AS q, 'ELEGANT' AS code, 'Lịch lãm, công sở' AS label, 1 AS ord,
             'lịch lãm thanh lịch công sở' AS keywords
      UNION ALL SELECT 'AM', 'SPORTY', 'Năng động, thể thao', 2, 'năng động thể thao sneaker'
      UNION ALL SELECT 'AM', 'STREET', 'Cá tính, phong cách Hàn Quốc', 3,
                       'cá tính hàn quốc ulzzang'
      UNION ALL SELECT 'AM', 'BASIC', 'Đơn giản, dễ phối đồ', 4, 'đơn giản basic trơn tối giản'
      UNION ALL SELECT 'AM', 'CLASSIC', 'Cổ điển, vintage', 5, 'cổ điển vintage retro'
      UNION ALL SELECT 'AM', 'ANY', 'Không theo gu nào cố định', 6, NULL
      UNION ALL SELECT 'AF', 'ELEGANT', 'Thanh lịch, công sở', 1, 'thanh lịch công sở'
      UNION ALL SELECT 'AF', 'FEMININE', 'Nữ tính, dễ thương', 2, 'nữ tính dễ thương nơ'
      UNION ALL SELECT 'AF', 'SPORTY', 'Năng động, thể thao', 3, 'năng động thể thao sneaker'
      UNION ALL SELECT 'AF', 'STREET', 'Cá tính, phong cách Hàn Quốc', 4,
                       'cá tính hàn quốc ulzzang'
      UNION ALL SELECT 'AF', 'LUXURY', 'Sang trọng, nổi bật', 5, 'sang trọng lấp lánh đính đá'
      UNION ALL SELECT 'AF', 'BASIC', 'Đơn giản, dễ phối đồ', 6, 'đơn giản basic trơn tối giản'
      UNION ALL SELECT 'AF', 'ANY', 'Không theo gu nào cố định', 7, NULL) v ON v.q = q.code;

-- Q2, G2: thêm dịp đi tiệc và từ khóa cho từng dịp.
INSERT INTO survey_option (question_id, code, label, display_order)
SELECT q.id, 'PARTY', 'Đi tiệc, sự kiện', 6
FROM survey_question q
WHERE q.code IN ('Q2', 'G2');

INSERT INTO survey_option_category (option_id, category_name)
SELECT o.id, m.category_name
FROM survey_option o
JOIN survey_question q ON q.id = o.question_id AND q.code IN ('Q2', 'G2')
JOIN (SELECT 'Giày tây nam' AS category_name
      UNION ALL SELECT 'Giày lười nam'
      UNION ALL SELECT 'Giày cao gót nữ'
      UNION ALL SELECT 'Giày búp bê nữ'
      UNION ALL SELECT 'Túi xách nam'
      UNION ALL SELECT 'Túi xách nữ'
      UNION ALL SELECT 'Túi đeo chéo nữ'
      UNION ALL SELECT 'Ví nam'
      UNION ALL SELECT 'Ví nữ'
      UNION ALL SELECT 'Thắt lưng nam'
      UNION ALL SELECT 'Thắt lưng nữ'
      UNION ALL SELECT 'Ghim cài áo'
      UNION ALL SELECT 'Phụ kiện tóc') m
WHERE o.code = 'PARTY';

-- Ví cũng là đồ đi làm: người chọn "Ví, thắt lưng" và dịp đi làm không mất phần ví.
INSERT INTO survey_option_category (option_id, category_name)
SELECT o.id, m.category_name
FROM survey_option o
JOIN survey_question q ON q.id = o.question_id AND q.code IN ('Q2', 'G2')
JOIN (SELECT 'Ví nam' AS category_name UNION ALL SELECT 'Ví nữ') m
WHERE o.code = 'WORK';

UPDATE survey_option o
JOIN survey_question q ON q.id = o.question_id AND q.code IN ('Q2', 'G2')
SET o.keywords = CASE o.code
        WHEN 'SCHOOL' THEN 'đi học học sinh sinh viên'
        WHEN 'WORK' THEN 'công sở văn phòng đi làm'
        WHEN 'TRAVEL' THEN 'du lịch phượt chống nước'
        WHEN 'SPORT' THEN 'thể thao chạy bộ tập gym thoáng khí'
        WHEN 'OUTING' THEN 'dạo phố đi chơi'
        WHEN 'PARTY' THEN 'dự tiệc sang trọng'
    END;

-- ============================================================
-- Nối lại đường đi
-- ============================================================
UPDATE survey_option o
JOIN survey_question q ON q.id = o.question_id
JOIN survey_question n ON n.code = CASE
        WHEN q.code = 'Q1' AND o.code = 'SELF_MALE' THEN 'N1'
        WHEN q.code = 'Q1' AND o.code = 'SELF_FEMALE' THEN 'F1'
        WHEN q.code = 'N1' THEN 'AM'
        WHEN q.code = 'F1' THEN 'AF'
        WHEN q.code IN ('AM', 'AF') THEN 'Q2'
        WHEN q.code = 'Q2' THEN 'Q3'
        WHEN q.code = 'G1' AND o.code = 'MALE' THEN 'GN'
        WHEN q.code = 'G1' AND o.code = 'FEMALE' THEN 'GF'
        WHEN q.code IN ('GN', 'GF', 'G1') THEN 'G2'
        WHEN q.code = 'G2' THEN 'G3'
    END
SET o.next_question_id = n.id;
