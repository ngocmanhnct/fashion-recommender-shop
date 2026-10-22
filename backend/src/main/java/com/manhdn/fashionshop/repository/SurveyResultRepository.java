package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.SurveyResult;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng survey_result, khóa chính là mã tài khoản. */
public interface SurveyResultRepository extends JpaRepository<SurveyResult, Long> {}
