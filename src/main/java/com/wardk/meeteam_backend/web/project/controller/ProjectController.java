package com.wardk.meeteam_backend.web.project.controller;

import com.wardk.meeteam_backend.domain.project.service.ProjectCommandService;
import com.wardk.meeteam_backend.domain.project.service.ProjectQueryService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectPostRequest;
import com.wardk.meeteam_backend.web.project.dto.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 프로젝트 기본 CRUD API 컨트롤러.
 * 프로젝트 생성, 조회, 삭제를 담당합니다.
 */
@Tag(name = "Project", description = "프로젝트 API")
@RestController
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectCommandService projectCommandService;
    private final ProjectQueryService projectQueryService;

    @Operation(summary = "프로젝트 생성",
            description = "새 프로젝트를 생성합니다. 직군/직무/기술스택 정보는 GET /api/v1/jobs/options를 먼저 호출하여 조회하고, 선택한 직군(JobField)에 해당하는 기술스택만 전송해야 합니다. 마감 방식: END_DATE(마감 날짜 기반, endDate 필수), RECRUITMENT_COMPLETED(모집 완료 시, endDate 미전송)")
    @PostMapping(value = "/api/v1/projects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SuccessResponse<ProjectPostResponse> createProject(
            @Parameter(description = "프로젝트 생성 정보", required = true)
            @RequestPart @Validated ProjectPostRequest request,
            @Parameter(description = "프로젝트 대표 이미지", required = false)
            @RequestPart(name = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        ProjectPostResponse response = projectCommandService.create(request.toCommand(), file, userDetails.getUsername());
        return SuccessResponse.onSuccess(response);
    }

    @Operation(summary = "프로젝트 상세 조회", description = "프로젝트 상세 정보를 조회합니다. 프로젝트 리더의 관심분야와 기술스택 정보, 좋아요 수, 현재 사용자의 좋아요/리더 여부를 포함합니다.")
    @GetMapping("/api/v1/projects/{projectId}")
    public SuccessResponse<ProjectDetailResponse> findProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        Long memberId = userDetails != null ? userDetails.getMemberId() : null;
        ProjectDetailResponse project = projectQueryService.findById(projectId, memberId);
        return SuccessResponse.onSuccess(project);
    }

    @Operation(summary = "프로젝트 삭제", description = "프로젝트를 삭제합니다. 프로젝트 리더만 삭제할 수 있습니다.")
    @DeleteMapping("/api/v1/projects/{projectId}")
    public SuccessResponse<ProjectDeleteResponse> deleteProjectV1(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        ProjectDeleteResponse response = projectCommandService.delete(projectId, userDetails.getUsername());
        return SuccessResponse.onSuccess(response);
    }


    // ================== Legacy ======================


    @Deprecated
    @Operation(summary = "프로젝트 삭제", description = "프로젝트를 삭제합니다.", deprecated = true)
    @DeleteMapping("/api/projects/{projectId}")
    public SuccessResponse<ProjectDeleteResponse> deleteProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        ProjectDeleteResponse response = projectCommandService.delete(projectId, userDetails.getUsername());
        return SuccessResponse.onSuccess(response);
    }

    @Deprecated
    @Operation(summary = "내 프로젝트 조회", description = "로그인한 사용자가 참여 중인/완료한 프로젝트를 조회합니다.", deprecated = true)
    @GetMapping("/api/projects/my")
    public SuccessResponse<List<MyProjectResponse>> findMyProjects(
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        List<MyProjectResponse> myProjects = projectQueryService.findMyProjects(userDetails);
        return SuccessResponse.onSuccess(myProjects);
    }

    @Deprecated
    @Operation(summary = "프로젝트 목록 조회", description = "전체 프로젝트 목록을 조회합니다.", deprecated = true)
    @GetMapping("/api/projects")
    public SuccessResponse<List<ProjectListResponse>> findAllProjects() {
        List<ProjectListResponse> projects = projectQueryService.findAll();
        return SuccessResponse.onSuccess(projects);
    }
}