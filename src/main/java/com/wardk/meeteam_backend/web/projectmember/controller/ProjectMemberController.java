package com.wardk.meeteam_backend.web.projectmember.controller;

import com.wardk.meeteam_backend.domain.projectmember.service.ProjectMemberService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.projectmember.dto.request.*;
import com.wardk.meeteam_backend.web.projectmember.dto.response.*;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/project-members")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @Deprecated
    @Operation(summary = "프로젝트 멤버 전체 조회", deprecated = true)
    @GetMapping("/{projectId}")
    public SuccessResponse<List<ProjectMemberListResponse>> getProjectMembers(@PathVariable Long projectId) {

        List<ProjectMemberListResponse> projectMembers = projectMemberService.getProjectMembers(projectId);

        return SuccessResponse.onSuccess(projectMembers);
    }

    @Deprecated
    @Operation(summary = "프로젝트 멤버 삭제(추방)", deprecated = true)
    @PostMapping
    public SuccessResponse<DeleteResponse> deleteMembers(@RequestBody @Validated DeleteRequest request,
                                                         @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {

        DeleteResponse response = projectMemberService.deleteProjectMember(request, userDetails.getUsername());

        return SuccessResponse.onSuccess(response);
    }

    @Deprecated
    @Operation(summary = "프로젝트 멤버 자진 탈퇴", deprecated = true)
    @PostMapping("/withdraw")
    public SuccessResponse<WithdrawResponse> withdraw(@RequestBody @Validated WithdrawRequest request,
                                                      @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {

        WithdrawResponse response = projectMemberService.withdraw(request, userDetails.getUsername());


        return SuccessResponse.onSuccess(response);
    }
}
