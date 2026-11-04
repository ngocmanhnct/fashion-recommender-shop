-- Phụ kiện, vệ sinh giày (dây giày, xịt vệ sinh...) dùng chung cho nam và nữ, nhưng V11 chỉ gắn vào
-- đáp án "Phụ kiện" của đồ nam (N1, GN). Khách nữ chọn "Phụ kiện" + "Thể thao" vì vậy không bao giờ
-- được gợi ý danh mục này, dù câu dịp sử dụng có nó. Thêm vào đáp án ACCESSORY của F1 và GF.
INSERT INTO survey_option_category (option_id, category_name)
SELECT o.id, 'Phụ kiện, vệ sinh giày'
FROM survey_option o
JOIN survey_question q ON q.id = o.question_id
WHERE q.code IN ('F1', 'GF') AND o.code = 'ACCESSORY';
