package com.wardk.meeteam_backend.web.project.dto.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

/**
 * 프로젝트 검색 정렬 타입.
 */
@Getter
@RequiredArgsConstructor
public enum ProjectSortType {

    LATEST("createdAt", Direction.DESC),
    DEADLINE("endDate", Direction.ASC);

    private final String field;
    private final Direction direction;

    /**
     * Sort 객체로 변환합니다.
     *
     * @return 정렬 정보
     */
    public Sort toSort() {
        return Sort.by(direction, field);
    }
}
