package com.manhdn.fashionshop.dto;

import java.util.List;

/**
 * Khảo sát lần đầu của tài khoản đang đăng nhập.
 *
 * @param status NONE (chưa làm, chưa bỏ qua), COMPLETED hoặc SKIPPED
 * @param answers câu trả lời của lần làm gần nhất, rỗng nếu chưa làm
 * @param profile hồ sơ mua sắm, null nếu chưa làm
 */
public record MySurveyResponse(
    String status, List<SurveyAnswer> answers, SurveyProfileResponse profile) {}
