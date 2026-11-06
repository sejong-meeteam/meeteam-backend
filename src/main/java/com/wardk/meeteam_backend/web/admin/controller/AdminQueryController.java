package com.wardk.meeteam_backend.web.admin.controller;

import com.wardk.meeteam_backend.domain.admin.dto.AdminQueryOptions;
import com.wardk.meeteam_backend.domain.admin.service.AdminQueryService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Authentication and ADMIN authorization are enforced by the global admin namespace guard. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminQueryController {
    private final AdminQueryService service;
    private final com.wardk.meeteam_backend.domain.admin.service.AdminDashboardService dashboard;

    @GetMapping("/dashboard")
    public SuccessResponse<Dashboard> dashboard() {
        return SuccessResponse.onSuccess(dashboard.dashboard());
    }

    @GetMapping("/members")
    public SuccessResponse<Page<MemberSummary>> members(@RequestParam Map<String, String> params) {
        return SuccessResponse.onSuccess(service.members(options(params), params.get("status"), params.get("role"), params.get("jobFieldCode")));
    }
    @GetMapping("/members/{memberId}")
    public SuccessResponse<MemberDetail> member(@PathVariable long memberId) {
        return SuccessResponse.onSuccess(service.member(memberId));
    }
    @GetMapping("/projects")
    public SuccessResponse<Page<ProjectSummary>> projects(@RequestParam Map<String, String> params) {
        return SuccessResponse.onSuccess(service.projects(options(params), params.get("category"), params.get("recruitmentStatus"), params.get("visibility")));
    }
    @GetMapping("/projects/{projectId}")
    public SuccessResponse<ProjectDetail> project(@PathVariable long projectId) {
        return SuccessResponse.onSuccess(service.project(projectId));
    }
    private static AdminQueryOptions options(Map<String, String> params) {
        return AdminQueryOptions.of(params.get("page"), params.get("size"), params.get("keyword"), params.get("sort"));
    }
}
