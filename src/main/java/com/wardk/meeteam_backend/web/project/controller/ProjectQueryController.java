package com.wardk.meeteam_backend.web.project.controller;

import com.wardk.meeteam_backend.domain.project.service.ProjectQueryService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import com.wardk.meeteam_backend.web.project.dto.request.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "프로젝트 조회", description = "프로젝트 검색 및 조회 API")
@RestController
@RequiredArgsConstructor
public class ProjectQueryController {

    private final ProjectQueryService projectQueryService;


    @Operation(summary = "프로젝트 검색",
            description = "키워드, 카테고리, 모집상태, 플랫폼, 분야 조건으로 검색. 정렬: LATEST(최신순), DEADLINE(마감임박순). 무한스크롤 최적화(Slice).")
    @GetMapping("/api/v1/projects/search")
    public SuccessResponse<Slice<ProjectCardResponse>> searchProjects(
            @ParameterObject @Validated ProjectSearchRequest request,
            @ParameterObject @PageableDefault(page = 0, size = 20) Pageable pageable,
            @AuthenticationPrincipal(errorOnInvalidType = false) CustomSecurityUserDetails userDetails) {

        return SuccessResponse.onSuccess(
                projectQueryService.searchV1(request, pageable, userDetails));
    }



    // ===============Legacy===================

    @Deprecated
    @Operation(summary = "프로젝트 조건 검색", description = "조건과 페이징을 기반으로 프로젝트 목록을 조회합니다.", deprecated = true)
    @GetMapping("api/projects/condition")
    public Page<ProjectCardResponse> searchCondition(
            @AuthenticationPrincipal(errorOnInvalidType = false) CustomSecurityUserDetails userDetails,
            @ParameterObject ProjectSearchCondition condition,
            @ParameterObject Pageable pageable) {

        return projectQueryService.search(condition, pageable, userDetails);
    }


}