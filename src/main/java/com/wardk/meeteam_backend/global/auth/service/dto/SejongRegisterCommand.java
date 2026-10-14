package com.wardk.meeteam_backend.global.auth.service.dto;

import com.wardk.meeteam_backend.domain.member.entity.Gender;
import com.wardk.meeteam_backend.web.auth.dto.sejong.SejongRegisterRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * 세종대 포털 인증 후 회원가입 요청을 서비스 레이어로 전달하기 위한 Command 객체.
 */
public record SejongRegisterCommand(
        String code,
        String name,
        LocalDate birthDate,
        Gender gender,
        List<MemberJobPositionCommand> jobPositions,
        String githubUrl,
        String blogUrl
) {
    public static SejongRegisterCommand from(SejongRegisterRequest request) {
        List<MemberJobPositionCommand> jobPositionCommands = request.getJobPositions().stream()
                .map(MemberJobPositionCommand::from)
                .toList();

        return new SejongRegisterCommand(
                request.getCode(),
                request.getName(),
                request.getBirthDate(),
                request.getGender(),
                jobPositionCommands,
                request.getGithubUrl(),
                request.getBlogUrl()
        );
    }
}