package com.wardk.meeteam_backend.domain.job.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 직군(JobField) 코드 Enum.
 * 시스템에서 사용하는 직군 분류를 정의합니다.
 */
@Getter
@RequiredArgsConstructor
public enum JobFieldCode {

    PLANNING("기획"),
    DESIGN("디자인"),
    FRONTEND("프론트"),
    BACKEND("백엔드"),
    AI("AI"),
    INFRA_OPERATION("인프라/운영");

    private final String displayName;
}
