package com.wardk.meeteam_backend.domain.application.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로젝트 지원 상태 Enum.
 */
@Getter
@RequiredArgsConstructor
public enum ApplicationStatus {

    PENDING("대기중"),
    ACCEPTED("승인됨"),
    REJECTED("거절됨"),
    CANCELLED("취소됨");

    private final String displayName;

    /**
     * 리더가 승인 또는 거절해 처리를 마친 상태인지 여부.
     */
    public boolean isDecided() {
        return this == ACCEPTED || this == REJECTED;
    }
}
