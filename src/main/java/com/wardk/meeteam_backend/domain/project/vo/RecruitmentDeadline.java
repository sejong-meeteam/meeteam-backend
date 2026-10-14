package com.wardk.meeteam_backend.domain.project.vo;

import com.wardk.meeteam_backend.domain.project.entity.RecruitmentDeadlineType;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;

import java.time.LocalDate;

/**
 * 모집 마감 정책을 캡슐화하는 값 객체(Value Object).
 * 생성 시점에 비즈니스 규칙을 검증합니다.
 */
public record RecruitmentDeadline(
        RecruitmentDeadlineType type,
        LocalDate endDate
) {
    public RecruitmentDeadline {
        validate(type, endDate);
    }

    private static void validate(RecruitmentDeadlineType type, LocalDate endDate) {
        if (type == RecruitmentDeadlineType.END_DATE) {
            validateEndDateType(endDate);
            return;
        }

        if (type == RecruitmentDeadlineType.RECRUITMENT_COMPLETED) {
            validateRecruitmentCompletedType(endDate);
        }
    }

    private static void validateEndDateType(LocalDate endDate) {
        if (endDate == null) {
            throw new CustomException(ErrorCode.INVALID_RECRUITMENT_DEADLINE_POLICY);
        }
        if (endDate.isBefore(LocalDate.now())) {
            throw new CustomException(ErrorCode.INVALID_PROJECT_DATE);
        }
    }

    private static void validateRecruitmentCompletedType(LocalDate endDate) {
        if (endDate != null) {
            throw new CustomException(ErrorCode.INVALID_RECRUITMENT_DEADLINE_POLICY);
        }
    }

    /**
     * 마감 타입에 따라 종료일을 반환합니다.
     * END_DATE 타입인 경우 endDate를, RECRUITMENT_COMPLETED 타입인 경우 null을 반환합니다.
     */
    public LocalDate resolveEndDate() {
        return type == RecruitmentDeadlineType.END_DATE ? endDate : null;
    }
}