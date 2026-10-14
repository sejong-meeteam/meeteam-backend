package com.wardk.meeteam_backend.web.job.controller;

import com.wardk.meeteam_backend.domain.job.service.JobInfoService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.job.dto.response.JobOptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Job", description = "직군/포지션/기술스택 조회 API")
public class JobController {

    private final JobInfoService jobInfoService;

    @Operation(summary = "직군 정보 전체 조회", description = "필드, 포지션, 기술스택과 필드별 기술 스택 정보 조회")
    @GetMapping("/api/v1/jobs/options")
    public SuccessResponse<JobOptionResponse> getJobOptions() {
        return SuccessResponse.onSuccess(jobInfoService.getJobOptions());
    }
}
