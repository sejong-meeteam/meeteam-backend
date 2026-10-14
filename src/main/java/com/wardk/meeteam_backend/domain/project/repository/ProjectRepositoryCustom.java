package com.wardk.meeteam_backend.domain.project.repository;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.web.mainpage.dto.request.CategoryCondition;
import com.wardk.meeteam_backend.web.project.dto.request.*;
import com.wardk.meeteam_backend.web.project.dto.response.*;
import com.wardk.meeteam_backend.web.project.dto.request.*;
import com.wardk.meeteam_backend.web.project.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ProjectRepositoryCustom {

    /**
     * @deprecated Page 대신 Slice를 반환하는 {@link #searchWithSlice} 사용 권장 (무한스크롤 최적화)
     */
    @Deprecated
    Page<Project> findAllSlicedForSearchAtCondition(ProjectSearchCondition condition, Pageable pageable);

    /**
     * 무한스크롤용 검색 (count 쿼리 없음).
     */
    Slice<Project> searchWithSlice(ProjectSearchCondition condition, Pageable pageable);

    Page<Project> findProjectsFromMainPageCondition(CategoryCondition condition, Pageable pageable);
}
