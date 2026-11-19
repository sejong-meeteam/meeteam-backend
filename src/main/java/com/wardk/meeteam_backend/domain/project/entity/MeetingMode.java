package com.wardk.meeteam_backend.domain.project.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

/**
 * 프로젝트 회의 방식.
 */
@Getter
@RequiredArgsConstructor
public enum MeetingMode {
    ONLINE("온라인"),
    OFFLINE("대면"),
    HYBRID("온·오프라인 병행");

    private final String displayName;

    /**
     * 이 방식으로 검색했을 때 결과에 포함할 회의 방식.
     * 온라인으로 찾는 사람은 병행 프로젝트에도 온라인으로 참여할 수 있습니다.
     */
    public Set<MeetingMode> searchMatches() {
        return this == ONLINE ? EnumSet.of(ONLINE, HYBRID) : EnumSet.of(this);
    }
}
