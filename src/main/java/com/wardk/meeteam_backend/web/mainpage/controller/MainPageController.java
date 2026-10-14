package com.wardk.meeteam_backend.web.mainpage.controller;

import com.wardk.meeteam_backend.domain.member.service.MemberProfileService;
import com.wardk.meeteam_backend.domain.project.service.ProjectQueryService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.mainpage.dto.request.CategoryCondition;
import com.wardk.meeteam_backend.web.mainpage.dto.response.MemberCardResponse;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "메인페이지", description = "메인페이지 API")
@RequestMapping("/api/v1/main")
@RequiredArgsConstructor
@RestController
public class MainPageController {

    private final ProjectQueryService projectQueryService;
    private final MemberProfileService memberProfileService;


    @Operation(summary = "프로젝트 메인 페이지", description = "메인 페이지에서 프로젝트 카드를 조회")
    @GetMapping("/projects")
    public SuccessResponse<Page<ProjectCardResponse>> getProjects(
            @ParameterObject CategoryCondition condition,
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {

        return SuccessResponse.onSuccess(projectQueryService.searchForMainPage(condition, pageable, userDetails));

    }

    @Operation(summary = "유저 메인 페이지", description = "메인 페이지에서 유저 카드를 조회합니다. 기술스택은 displayOrder 순서대로 정렬되어 반환됩니다.")
    @GetMapping("/members")
    public SuccessResponse<Page<MemberCardResponse>> getMembers(
            @ParameterObject @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {

        return SuccessResponse.onSuccess(memberProfileService.getMainPageMembers(pageable));
    }
}