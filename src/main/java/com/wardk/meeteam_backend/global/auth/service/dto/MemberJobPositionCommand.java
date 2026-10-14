package com.wardk.meeteam_backend.global.auth.service.dto;

import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.web.auth.dto.register.MemberJobPositionRequest;

import java.util.List;

/**
 * 회원가입 시 직군/직무/기술스택 정보를 담는 Command 객체.
 * 웹 계층과 서비스 계층을 분리합니다.
 */
public record MemberJobPositionCommand(
        JobFieldCode jobFieldCode,
        JobPositionCode jobPositionCode,
        List<TechStackOrderCommand> techStacks
) {
    public static MemberJobPositionCommand from(MemberJobPositionRequest request) {
        List<TechStackOrderCommand> techStackCommands = request.techStacks().stream()
                .map(TechStackOrderCommand::from)
                .toList();

        return new MemberJobPositionCommand(
                request.jobFieldCode(),
                request.jobPositionCode(),
                techStackCommands
        );
    }
}