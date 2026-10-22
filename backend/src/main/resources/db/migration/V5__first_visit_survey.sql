-- V5__first_visit_survey.sql
--
-- Khảo sát lần đầu dạng rẽ nhánh theo phong cách mua sắm (US-23, BR-37, BR-38, BR-41, BR-42;
-- docs/dac-ta-yeu-cau.md Mục 3.12, 3.16). Không sửa V1-V4 vì Flyway kiểm tra checksum.
--
--   1. survey_question, survey_option, survey_option_category: cây câu hỏi lưu trong CSDL, mỗi
--      đáp án trỏ tới câu tiếp theo (NULL = kết thúc). Đổi câu hỏi không phải sửa code giao diện.
--   2. survey_result: trạng thái khảo sát của tài khoản (đã làm / đã bỏ qua) và phong cách mua
--      sắm. Danh mục, thương hiệu, khoảng giá vẫn lưu ở user_preference (V2).
--   3. Nạp sẵn nội dung cây câu hỏi.
--
-- Đáp án dịp sử dụng nối với danh mục qua TÊN danh mục (survey_option_category.category_name),
-- không qua khóa ngoại: danh mục mẫu được nạp lúc backend khởi động, sau khi migration đã chạy.
-- Danh mục có tên kết thúc bằng " nam" chỉ dùng cho đồ nam, " nữ" chỉ cho đồ nữ, còn lại dùng
-- chung (xử lý ở SurveyService).

-- ============================================================
-- 1. Cây câu hỏi
-- ============================================================
-- answer_type: SINGLE (chọn 1), MULTIPLE (chọn 1 đến max_choices), BRAND (chọn 1 thương hiệu
-- thuộc các danh mục đã chọn, danh sách lấy lúc làm khảo sát; câu tiếp theo ở
-- survey_question.next_question_id).
CREATE TABLE survey_question (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(10)      NOT NULL,
    content          VARCHAR(255)     NOT NULL,
    hint             VARCHAR(255)     NULL,
    answer_type      ENUM('SINGLE','MULTIPLE','BRAND') NOT NULL,
    max_choices      INT NOT NULL DEFAULT 1,
    next_question_id BIGINT UNSIGNED  NULL,
    CONSTRAINT uk_survey_question_code UNIQUE (code),
    CONSTRAINT fk_survey_question_next FOREIGN KEY (next_question_id) REFERENCES survey_question(id),
    CONSTRAINT ck_survey_question_max_choices CHECK (max_choices >= 1)
) ENGINE=InnoDB;

-- Mỗi cột "kết quả" (purpose ... min_rating) là thông tin đáp án đóng góp vào hồ sơ khách;
-- NULL = đáp án không đổi thông tin đó.
--   purpose        SELF (mua cho bản thân) / GIFT (tìm quà, kết quả không lưu lâu dài, BR-42)
--   gender         MALE / FEMALE: chỉ lấy danh mục nam / nữ (cộng danh mục dùng chung)
--   style          phong cách mua sắm: SAVER, BRAND, QUALITY, TREND (Sproles & Kendall 1986)
--   price_min/max  khoảng giá, VNĐ; price_max NULL = không giới hạn trên
--   price_flexible nới trần giá thêm 30% (câu S2)
--   brand_loyalty  STRONG / OPEN (câu B2)
--   min_rating     ngưỡng điểm đánh giá (câu C1)
CREATE TABLE survey_option (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    question_id      BIGINT UNSIGNED  NOT NULL,
    code             VARCHAR(20)      NOT NULL,
    label            VARCHAR(200)     NOT NULL,
    display_order    INT NOT NULL,
    next_question_id BIGINT UNSIGNED  NULL,
    purpose          ENUM('SELF','GIFT') NULL,
    gender           ENUM('MALE','FEMALE') NULL,
    style            ENUM('SAVER','BRAND','QUALITY','TREND') NULL,
    price_min        BIGINT UNSIGNED  NULL,
    price_max        BIGINT UNSIGNED  NULL,
    price_flexible   BOOLEAN          NULL,
    brand_loyalty    ENUM('STRONG','OPEN') NULL,
    min_rating       DECIMAL(2, 1)    NULL,
    CONSTRAINT uk_survey_option_question_code UNIQUE (question_id, code),
    CONSTRAINT fk_survey_option_question FOREIGN KEY (question_id) REFERENCES survey_question(id),
    CONSTRAINT fk_survey_option_next FOREIGN KEY (next_question_id) REFERENCES survey_question(id),
    CONSTRAINT ck_survey_option_price CHECK (price_max IS NULL OR price_min IS NULL
                                             OR price_max >= price_min)
) ENGINE=InnoDB;

CREATE TABLE survey_option_category (
    option_id     BIGINT UNSIGNED NOT NULL,
    category_name VARCHAR(100)    NOT NULL,
    PRIMARY KEY (option_id, category_name),
    CONSTRAINT fk_survey_option_category_option FOREIGN KEY (option_id) REFERENCES survey_option(id)
) ENGINE=InnoDB;

-- ============================================================
-- 2. Kết quả khảo sát của tài khoản
-- ============================================================
-- Một dòng mỗi tài khoản. status COMPLETED: đã làm hết (nhánh mua cho bản thân); SKIPPED: đã bỏ
-- qua, không tự hiện lại (BR-37). answers là các câu trả lời đã chọn (JSON) để làm lại khảo sát
-- và dựng lại hồ sơ. Làm lại khảo sát ghi đè dòng này và các dòng user_preference.
CREATE TABLE survey_result (
    account_id    BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    status        ENUM('COMPLETED','SKIPPED') NOT NULL,
    style         ENUM('SAVER','BRAND','QUALITY','TREND') NULL,
    brand_loyalty ENUM('STRONG','OPEN') NULL,
    min_rating    DECIMAL(2, 1)   NULL,
    answers       JSON            NULL,
    created_at    DATETIME        NOT NULL,
    updated_at    DATETIME        NOT NULL,
    CONSTRAINT fk_survey_result_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB;

-- ============================================================
-- 3. Nội dung cây câu hỏi
-- ============================================================
-- Đường đi (số câu): bản thân + tiết kiệm Q1-Q2-Q3-S1-S2 (5); thương hiệu Q1-Q2-Q3-B1-B2-P1 (6);
-- cầu toàn Q1-Q2-Q3-C1-P1 (5); xu hướng Q1-Q2-Q3-P1 (4); quà tặng Q1-G1-G2-G3 (4). BR-41.
-- Tạo câu hỏi trước (chưa nối), thêm đáp án, rồi nối câu tiếp theo theo mã.
INSERT INTO survey_question (code, content, hint, answer_type, max_choices) VALUES
    ('Q1', 'Hôm nay bạn ghé shop để…', NULL, 'SINGLE', 1),
    ('Q2', 'Bạn cần đồ cho dịp nào?', 'Chọn tối đa 3 dịp', 'MULTIPLE', 3),
    ('Q3', 'Khi chọn mua, điều gì quan trọng nhất với bạn?', NULL, 'SINGLE', 1),
    ('S1', 'Bạn thường chi bao nhiêu cho một món?', NULL, 'SINGLE', 1),
    ('S2', 'Gặp món ưng ý nhưng hơi quá ngân sách thì bạn…', NULL, 'SINGLE', 1),
    ('B1', 'Thương hiệu bạn tin dùng?', 'Các thương hiệu có trong những danh mục bạn quan tâm',
     'BRAND', 1),
    ('B2', 'Bạn có hay đổi sang thương hiệu khác không?', NULL, 'SINGLE', 1),
    ('C1', 'Bạn chấp nhận sản phẩm được đánh giá từ bao nhiêu sao?', NULL, 'SINGLE', 1),
    ('P1', 'Tầm giá bạn thường mua?', NULL, 'SINGLE', 1),
    ('G1', 'Người nhận quà là…', NULL, 'SINGLE', 1),
    ('G2', 'Người nhận hay dùng vào việc gì?', 'Chọn tối đa 3 dịp', 'MULTIPLE', 3),
    ('G3', 'Ngân sách cho món quà?', NULL, 'SINGLE', 1);

-- Q1: mục đích và giới tính của đồ cần tìm
INSERT INTO survey_option (question_id, code, label, display_order, purpose, gender)
SELECT q.id, v.code, v.label, v.ord, v.purpose, v.gender
FROM survey_question q
JOIN (SELECT 'SELF_MALE' AS code, 'Tìm đồ nam cho bản thân' AS label, 1 AS ord,
             'SELF' AS purpose, 'MALE' AS gender
      UNION ALL SELECT 'SELF_FEMALE', 'Tìm đồ nữ cho bản thân', 2, 'SELF', 'FEMALE'
      UNION ALL SELECT 'GIFT', 'Tìm quà tặng người khác', 3, 'GIFT', NULL) v
WHERE q.code = 'Q1';

-- Q2 và G2: dịp sử dụng (cùng bộ đáp án, cùng ánh xạ danh mục)
INSERT INTO survey_option (question_id, code, label, display_order)
SELECT q.id, v.code, v.label, v.ord
FROM survey_question q
JOIN (SELECT 'SCHOOL' AS code, 'Đi học' AS label, 1 AS ord
      UNION ALL SELECT 'WORK', 'Đi làm', 2
      UNION ALL SELECT 'TRAVEL', 'Du lịch, công tác', 3
      UNION ALL SELECT 'SPORT', 'Thể thao, vận động', 4
      UNION ALL SELECT 'OUTING', 'Đi chơi, dạo phố', 5) v
WHERE q.code IN ('Q2', 'G2');

INSERT INTO survey_option_category (option_id, category_name)
SELECT o.id, m.category_name
FROM survey_option o
JOIN survey_question q ON q.id = o.question_id AND q.code IN ('Q2', 'G2')
JOIN (SELECT 'SCHOOL' AS code, 'Balo laptop' AS category_name
      UNION ALL SELECT 'SCHOOL', 'Balo thời trang'
      UNION ALL SELECT 'SCHOOL', 'Giày thể thao nam'
      UNION ALL SELECT 'SCHOOL', 'Giày thể thao nữ'
      UNION ALL SELECT 'SCHOOL', 'Ví nam'
      UNION ALL SELECT 'SCHOOL', 'Ví nữ'
      UNION ALL SELECT 'SCHOOL', 'Vớ nam'
      UNION ALL SELECT 'SCHOOL', 'Vớ nữ'
      UNION ALL SELECT 'SCHOOL', 'Khẩu trang'
      UNION ALL SELECT 'WORK', 'Giày tây nam'
      UNION ALL SELECT 'WORK', 'Giày lười nam'
      UNION ALL SELECT 'WORK', 'Giày cao gót nữ'
      UNION ALL SELECT 'WORK', 'Giày búp bê nữ'
      UNION ALL SELECT 'WORK', 'Giày lười nữ'
      UNION ALL SELECT 'WORK', 'Túi xách nam'
      UNION ALL SELECT 'WORK', 'Túi xách nữ'
      UNION ALL SELECT 'WORK', 'Cặp, túi chống sốc laptop'
      UNION ALL SELECT 'WORK', 'Thắt lưng nam'
      UNION ALL SELECT 'WORK', 'Thắt lưng nữ'
      UNION ALL SELECT 'WORK', 'Gọng kính'
      UNION ALL SELECT 'WORK', 'Ghim cài áo'
      UNION ALL SELECT 'TRAVEL', 'Vali'
      UNION ALL SELECT 'TRAVEL', 'Balo du lịch'
      UNION ALL SELECT 'TRAVEL', 'Túi đựng đồ du lịch'
      UNION ALL SELECT 'TRAVEL', 'Phụ kiện du lịch'
      UNION ALL SELECT 'TRAVEL', 'Kính mát'
      UNION ALL SELECT 'TRAVEL', 'Nón nam'
      UNION ALL SELECT 'TRAVEL', 'Nón nữ'
      UNION ALL SELECT 'TRAVEL', 'Khăn'
      UNION ALL SELECT 'SPORT', 'Giày thể thao nam'
      UNION ALL SELECT 'SPORT', 'Giày thể thao nữ'
      UNION ALL SELECT 'SPORT', 'Vớ nam'
      UNION ALL SELECT 'SPORT', 'Vớ nữ'
      UNION ALL SELECT 'SPORT', 'Găng tay'
      UNION ALL SELECT 'SPORT', 'Nón nam'
      UNION ALL SELECT 'SPORT', 'Nón nữ'
      UNION ALL SELECT 'SPORT', 'Phụ kiện, vệ sinh giày'
      UNION ALL SELECT 'OUTING', 'Sandal nam'
      UNION ALL SELECT 'OUTING', 'Sandal nữ'
      UNION ALL SELECT 'OUTING', 'Dép nam'
      UNION ALL SELECT 'OUTING', 'Dép nữ'
      UNION ALL SELECT 'OUTING', 'Giày boots nam'
      UNION ALL SELECT 'OUTING', 'Giày boots nữ'
      UNION ALL SELECT 'OUTING', 'Giày đế xuồng nữ'
      UNION ALL SELECT 'OUTING', 'Túi tote nữ'
      UNION ALL SELECT 'OUTING', 'Túi đeo chéo nữ'
      UNION ALL SELECT 'OUTING', 'Túi đeo chéo nam'
      UNION ALL SELECT 'OUTING', 'Kính mát'
      UNION ALL SELECT 'OUTING', 'Phụ kiện tóc') m ON m.code = o.code;

-- Q3: phong cách mua sắm
INSERT INTO survey_option (question_id, code, label, display_order, style)
SELECT q.id, v.code, v.label, v.ord, v.style
FROM survey_question q
JOIN (SELECT 'PRICE' AS code, 'Giá hợp lý' AS label, 1 AS ord, 'SAVER' AS style
      UNION ALL SELECT 'BRAND', 'Thương hiệu', 2, 'BRAND'
      UNION ALL SELECT 'QUALITY', 'Bền, chất lượng tốt', 3, 'QUALITY'
      UNION ALL SELECT 'TREND', 'Đang thịnh hành, nhiều người mua', 4, 'TREND') v
WHERE q.code = 'Q3';

-- S1, P1, G3: khoảng giá (cùng 4 mức)
INSERT INTO survey_option (question_id, code, label, display_order, price_min, price_max)
SELECT q.id, v.code, v.label, v.ord, v.price_min, v.price_max
FROM survey_question q
JOIN (SELECT 'UNDER_200K' AS code, 'Dưới 200.000đ' AS label, 1 AS ord,
             0 AS price_min, 200000 AS price_max
      UNION ALL SELECT '200K_500K', 'Từ 200.000đ đến 500.000đ', 2, 200000, 500000
      UNION ALL SELECT '500K_1M', 'Từ 500.000đ đến 1.000.000đ', 3, 500000, 1000000
      UNION ALL SELECT 'OVER_1M', 'Trên 1.000.000đ', 4, 1000000, NULL) v
WHERE q.code IN ('S1', 'P1', 'G3');

-- S2: độ cứng của ngân sách
INSERT INTO survey_option (question_id, code, label, display_order, price_flexible)
SELECT q.id, v.code, v.label, v.ord, v.flexible
FROM survey_question q
JOIN (SELECT 'WAIT_SALE' AS code, 'Chờ giảm giá rồi mới mua' AS label, 1 AS ord,
             FALSE AS flexible
      UNION ALL SELECT 'CHEAPER', 'Tìm món tương tự rẻ hơn', 2, FALSE
      UNION ALL SELECT 'BUY_ANYWAY', 'Vẫn mua nếu thật sự thích', 3, TRUE) v
WHERE q.code = 'S2';

-- B2: mức ưu tiên thương hiệu
INSERT INTO survey_option (question_id, code, label, display_order, brand_loyalty)
SELECT q.id, v.code, v.label, v.ord, v.loyalty
FROM survey_question q
JOIN (SELECT 'RARELY' AS code, 'Hiếm khi đổi' AS label, 1 AS ord, 'STRONG' AS loyalty
      UNION ALL SELECT 'OPEN', 'Sẵn sàng thử thương hiệu mới', 2, 'OPEN') v
WHERE q.code = 'B2';

-- C1: ngưỡng điểm đánh giá
INSERT INTO survey_option (question_id, code, label, display_order, min_rating)
SELECT q.id, v.code, v.label, v.ord, v.min_rating
FROM survey_question q
JOIN (SELECT 'FROM_4' AS code, 'Từ 4 sao' AS label, 1 AS ord, 4.0 AS min_rating
      UNION ALL SELECT 'FROM_4_5', 'Từ 4,5 sao', 2, 4.5
      UNION ALL SELECT 'ANY', 'Không quan tâm', 3, NULL) v
WHERE q.code = 'C1';

-- G1: giới tính người nhận quà
INSERT INTO survey_option (question_id, code, label, display_order, gender)
SELECT q.id, v.code, v.label, v.ord, v.gender
FROM survey_question q
JOIN (SELECT 'MALE' AS code, 'Nam' AS label, 1 AS ord, 'MALE' AS gender
      UNION ALL SELECT 'FEMALE', 'Nữ', 2, 'FEMALE'
      UNION ALL SELECT 'UNSPECIFIED', 'Không muốn nói', 3, NULL) v
WHERE q.code = 'G1';

-- Nối câu tiếp theo. Đáp án không nối (S2, P1, G3) là câu cuối của đường đi.
UPDATE survey_option o
JOIN survey_question q ON q.id = o.question_id
JOIN survey_question n ON n.code = CASE
        WHEN q.code = 'Q1' AND o.code = 'GIFT' THEN 'G1'
        WHEN q.code = 'Q1' THEN 'Q2'
        WHEN q.code = 'Q2' THEN 'Q3'
        WHEN q.code = 'Q3' AND o.code = 'PRICE' THEN 'S1'
        WHEN q.code = 'Q3' AND o.code = 'BRAND' THEN 'B1'
        WHEN q.code = 'Q3' AND o.code = 'QUALITY' THEN 'C1'
        WHEN q.code = 'Q3' AND o.code = 'TREND' THEN 'P1'
        WHEN q.code = 'S1' THEN 'S2'
        WHEN q.code = 'B2' THEN 'P1'
        WHEN q.code = 'C1' THEN 'P1'
        WHEN q.code = 'G1' THEN 'G2'
        WHEN q.code = 'G2' THEN 'G3'
    END
SET o.next_question_id = n.id;

UPDATE survey_question q
JOIN survey_question n ON n.code = 'B2'
SET q.next_question_id = n.id
WHERE q.code = 'B1';
