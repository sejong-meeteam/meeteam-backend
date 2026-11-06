package com.wardk.meeteam_backend.web.admin.controller;

import com.wardk.meeteam_backend.web.admin.dto.request.AdminMutationRequests;

import com.fasterxml.jackson.databind.JsonNode;
import com.wardk.meeteam_backend.domain.admin.service.AdminMutationService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminMutationController {
    private final AdminMutationService service;
    private final com.wardk.meeteam_backend.domain.admin.service.AdminMemberActionService memberActions;
    private final com.wardk.meeteam_backend.domain.admin.service.AdminProjectActionService projectActions;
    @PostMapping("/members/{memberId}/suspend")
    public SuccessResponse<MemberSummary> suspend(@PathVariable long memberId, @RequestBody JsonNode body,
                                                  @AuthenticationPrincipal CustomSecurityUserDetails user) {
        var request = AdminMutationRequests.suspension(body);
        return SuccessResponse.onSuccess(service.suspend(user.getMemberId(), memberId, request.days(), request.reason()));
    }
    @PostMapping("/members/{memberId}/unsuspend")
    public SuccessResponse<MemberSummary> unsuspend(@PathVariable long memberId, @RequestBody JsonNode body,
                                                    @AuthenticationPrincipal CustomSecurityUserDetails user) {
        return SuccessResponse.onSuccess(service.unsuspend(user.getMemberId(), memberId, AdminMutationRequests.reason(body)));
    }
    @PatchMapping("/projects/{projectId}/visibility")
    public SuccessResponse<ProjectSummary> visibility(@PathVariable long projectId, @RequestBody JsonNode body,
                                                      @AuthenticationPrincipal CustomSecurityUserDetails user) {
        var request = AdminMutationRequests.visibility(body);
        return SuccessResponse.onSuccess(service.visibility(user.getMemberId(), projectId, request.visibility(), request.reason()));
    }

    @PatchMapping("/members/{memberId}/role")
    public SuccessResponse<MemberSummary> role(@PathVariable long memberId, @RequestBody JsonNode body,
                                               @AuthenticationPrincipal CustomSecurityUserDetails user) {
        var request = AdminMutationRequests.role(body);
        return SuccessResponse.onSuccess(memberActions.changeRole(user.getMemberId(), memberId, request.role(), request.reason()));
    }
    @PostMapping("/members/{memberId}/withdraw")
    public SuccessResponse<MemberSummary> withdraw(@PathVariable long memberId, @RequestBody JsonNode body,
                                                   @AuthenticationPrincipal CustomSecurityUserDetails user) {
        return SuccessResponse.onSuccess(memberActions.withdraw(user.getMemberId(), memberId, AdminMutationRequests.reason(body)));
    }
    @PostMapping("/projects/{projectId}/close")
    public SuccessResponse<ProjectSummary> close(@PathVariable long projectId, @RequestBody JsonNode body,
                                                 @AuthenticationPrincipal CustomSecurityUserDetails user) {
        return SuccessResponse.onSuccess(projectActions.close(user.getMemberId(), projectId, AdminMutationRequests.reason(body)));
    }
    @PostMapping("/projects/{projectId}/members/{memberId}/expel")
    public SuccessResponse<ProjectSummary> expel(@PathVariable long projectId, @PathVariable long memberId,
                                                 @RequestBody JsonNode body,
                                                 @AuthenticationPrincipal CustomSecurityUserDetails user) {
        return SuccessResponse.onSuccess(projectActions.expel(user.getMemberId(), projectId, memberId, AdminMutationRequests.reason(body)));
    }
}
