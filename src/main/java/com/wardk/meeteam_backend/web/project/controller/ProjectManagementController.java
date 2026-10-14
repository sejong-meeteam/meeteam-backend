package com.wardk.meeteam_backend.web.project.controller;

import com.wardk.meeteam_backend.domain.project.service.ProjectEditService;
import com.wardk.meeteam_backend.domain.project.service.ProjectManagementService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectEditRequest;
import com.wardk.meeteam_backend.web.project.dto.response.MemberExpelResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectEditPrefillResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectEditResponse;
import com.wardk.meeteam_backend.web.project.dto.response.RecruitmentStatusResponse;
import com.wardk.meeteam_backend.web.project.dto.response.TeamManagementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로젝트 관리 페이지 관련 API 컨트롤러.
 * 프로젝트 수정, 모집 상태 관리, 레포지토리 연결 등을 담당합니다.
 */
@Tag(name = "Project Management", description = "프로젝트 관리 API")
@RestController
@RequiredArgsConstructor
public class ProjectManagementController {

    private final ProjectManagementService projectManagementService;
    private final ProjectEditService projectEditService;

    @Operation(summary = "모집 상태 토글", description = "프로젝트 모집 상태를 토글합니다. (모집중 ↔ 모집중단)")
    @PostMapping("/api/v1/projects/{projectId}/recruitment/toggle")
    public SuccessResponse<RecruitmentStatusResponse> toggleRecruitmentStatus(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        RecruitmentStatusResponse response = projectManagementService.toggleRecruitmentStatus(projectId, userDetails.getUsername());
        return SuccessResponse.onSuccess(response);
    }

    @Operation(summary = "팀원 관리 정보 조회", description = "프로젝트 팀원 관리 페이지에 필요한 정보를 조회합니다. 현재 멤버 수, 총 모집 정원, 대기중인 지원서 수, 팀원 목록을 포함합니다.")
    @GetMapping("/api/v1/projects/{projectId}/team")
    public SuccessResponse<TeamManagementResponse> getTeamManagement(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        TeamManagementResponse response = projectManagementService.getTeamManagement(projectId, userDetails.getUsername());
        return SuccessResponse.onSuccess(response);
    }

    @Operation(summary = "프로젝트 수정 Pre-fill 조회", description = "프로젝트 수정 페이지에 필요한 기존 정보를 조회합니다. 리더만 조회 가능합니다.")
    @GetMapping("/api/v1/projects/{projectId}/edit")
    public SuccessResponse<ProjectEditPrefillResponse> getProjectEditPrefill(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        ProjectEditPrefillResponse response = projectEditService.getEditPrefill(projectId, userDetails.getUsername());
        return SuccessResponse.onSuccess(response);
    }

    @Operation(summary = "프로젝트 수정", description = "프로젝트 정보를 수정합니다. 리더만 수정 가능합니다. 모집 중단 상태에서는 수정 불가합니다.")
    @PutMapping(value = "/api/v1/projects/{projectId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SuccessResponse<ProjectEditResponse> updateProject(
            @PathVariable Long projectId,
            @RequestPart("request") @Valid ProjectEditRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        ProjectEditResponse response = projectEditService.update(
                projectId,
                request.toCommand(),
                file,
                userDetails.getUsername()
        );
        return SuccessResponse.onSuccess(response);
    }

    @Operation(summary = "팀원 방출", description = "프로젝트 팀원을 방출합니다. 리더만 방출 가능하며, 리더 본인은 방출할 수 없습니다.")
    @DeleteMapping("/api/v1/projects/{projectId}/members/{memberId}")
    public SuccessResponse<MemberExpelResponse> expelMember(
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        MemberExpelResponse response = projectManagementService.expelMember(
                projectId,
                memberId,
                userDetails.getUsername()
        );
        return SuccessResponse.onSuccess(response);
    }
}