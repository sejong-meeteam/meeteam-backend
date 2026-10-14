package com.wardk.meeteam_backend.web.project.dto.request;

import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.project.entity.PlatformCategory;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCategory;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 프로젝트 검색 V1 API 요청 DTO.
 */
@Schema(description = "프로젝트 검색 요청")
public record ProjectSearchRequest(
        @Size(min = 2, message = "키워드는 2글자 이상이어야 합니다")
        @Schema(description = "검색 키워드 (프로젝트명 또는 리더명)", example = "개발")
        String keyword,

        @Schema(description = "프로젝트 카테고리", example = "CAPSTONE")
        ProjectCategory projectCategory,

        @Schema(description = "모집 상태", example = "RECRUITING")
        Recruitment recruitment,

        @Schema(description = "플랫폼 카테고리", example = "WEB")
        PlatformCategory platformCategory,

        @Schema(description = "직군 (PLANNING, DESIGN, FRONTEND, BACKEND, AI, INFRA_OPERATION)", example = "BACKEND")
        JobFieldCode jobField,

        @Schema(description = "정렬 타입 (LATEST: 최신순, DEADLINE: 마감임박순)", example = "LATEST")
        ProjectSortType sort
) {
    /**
     * 기본 정렬값이 null인 경우 LATEST로 대체한 객체를 반환합니다.
     */
    public ProjectSearchRequest {
        if (sort == null) {
            sort = ProjectSortType.LATEST;
        }
    }

    /**
     * 기존 ProjectSearchCondition으로 변환합니다.
     *
     * @return 검색 조건 객체
     */
    public ProjectSearchCondition toCondition() {
        ProjectSearchCondition condition = new ProjectSearchCondition();
        condition.setKeyword(keyword);
        condition.setProjectCategory(projectCategory);
        condition.setRecruitment(recruitment);
        condition.setPlatformCategory(platformCategory);
        condition.setJobFieldCode(jobField);
        return condition;
    }
}
