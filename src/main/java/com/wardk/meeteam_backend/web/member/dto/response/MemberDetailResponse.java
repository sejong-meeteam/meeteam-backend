package com.wardk.meeteam_backend.web.member.dto.response;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.Gender;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * 특정 사용자 상세 조회 응답 DTO.
 */
@Schema(description = "회원 상세 정보")
@Getter
@Builder
public class MemberDetailResponse {

    @Schema(description = "회원 ID")
    private final Long memberId;

    @Schema(description = "프로필 이미지 URL")
    private final String profileImageUrl;

    @Schema(description = "이름")
    private final String name;

    @Schema(description = "나이", example = "23")
    private final Integer age;

    @Schema(description = "성별")
    private final Gender gender;

    @Schema(description = "대표 포지션 (영문)", example = "Frontend Dev")
    private final String representativePosition;

    @Schema(description = "직군 목록", example = "[\"프론트\", \"웹 프론트엔드\"]")
    private final List<String> jobPositions;

    @Schema(description = "이메일")
    private final String email;

    @Schema(description = "GitHub URL")
    private final String githubUrl;

    @Schema(description = "블로그 URL")
    private final String blogUrl;

    @Schema(description = "프로젝트 참여 희망 여부")
    private final Boolean isParticipating;

    @Schema(description = "자기소개")
    private final String introduce;

    @Schema(description = "참여 프로젝트 수")
    private final Integer participatedProjectCount;

    @Schema(description = "참여 프로젝트 목록")
    private final List<ProjectCardResponse> participatedProjects;

    @Schema(description = "보유 기술 목록 (직군과 무관하게 회원이 보유한 전체 기술)", example = "[\"React.js\", \"Spring\", \"SwiftUI\"]")
    private final List<String> skills;

    /**
     * Member 엔티티로부터 MemberDetailResponse 생성.
     *
     * @param member             회원 엔티티
     * @param participatedProjects 참여 프로젝트 카드 목록
     */
    public static MemberDetailResponse from(Member member, List<ProjectCardResponse> participatedProjects) {
        // 나이 계산
        Integer age = null;
        if (member.getBirth() != null) {
            age = Period.between(member.getBirth(), LocalDate.now()).getYears();
        }

        // 대표 포지션 (첫 번째 직무 포지션의 영문명)
        String representativePosition = null;
        if (!member.getJobPositions().isEmpty()) {
            JobPosition firstPosition = member.getJobPositions().get(0).getJobPosition();
            representativePosition = firstPosition.getCode().getEnglishName();
        }

        // 직군 목록 (포지션명 리스트)
        List<String> jobPositionNames = member.getJobPositions().stream()
                .map(mjp -> mjp.getJobPosition().getName())
                .toList();

        // 보유 기술 전체 (직군 종속 필터 없이 회원의 모든 기술을 displayOrder 순으로)
        List<String> skills = member.getMemberTechStacks().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(mts -> mts.getTechStack().getName())
                .toList();

        return MemberDetailResponse.builder()
                .memberId(member.getId())
                .profileImageUrl(member.getStoreFileName())
                .name(member.getRealName())
                .age(age)
                .gender(member.getGender())
                .representativePosition(representativePosition)
                .jobPositions(jobPositionNames)
                .email(member.getEmail())
                .githubUrl(member.getGithubUrl())
                .blogUrl(member.getBlogUrl())
                .isParticipating(member.getIsParticipating())
                .introduce(member.getIntroduction())
                .participatedProjectCount(participatedProjects.size())
                .participatedProjects(participatedProjects)
                .skills(skills)
                .build();
    }
}