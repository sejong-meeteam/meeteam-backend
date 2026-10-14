package com.wardk.meeteam_backend.web.member.dto.response;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.Gender;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MemberProfileResponse {


    private String name;
    private Long memberId;

    @Schema(description = "생년월일", example = "1998-03-15")
    private LocalDate birthDate;

    private Gender gender;
    private String email;

    @Schema(description = "GitHub URL")
    private String githubUrl;

    @Schema(description = "블로그 URL")
    private String blogUrl;

    @Schema(description = "대표 포지션 한글명", example = "웹 프론트엔드")
    private String representativePosition;

    @Schema(description = "대표 포지션 영문명", example = "Frontend Dev")
    private String representativePositionEn;

    @Schema(description = "보유 기술 목록 (직군과 무관하게 회원이 보유한 전체 기술)", example = "[\"React.js\", \"Spring\", \"SwiftUI\"]")
    private List<String> skills;

    private Boolean isParticipating;
    private int projectCount;
    private String introduce;

    @Schema(description = "프로필 이미지 URL")
    private String profileImageUrl;

    @Schema(description = "프로필 이미지 파일명")
    private String profileImageName;

    @Schema(description = "참여 프로젝트 카드 목록")
    private List<ProjectCardResponse> projectCards;

    public MemberProfileResponse(Member member, Long memberId) {
        this.memberId = memberId;
        this.name = member.getRealName();
        this.birthDate = member.getBirth();
        this.gender = member.getGender();
        this.email = member.getEmail();
        this.githubUrl = member.getGithubUrl();
        this.blogUrl = member.getBlogUrl();

        // 대표 포지션 (첫 번째 직무 포지션)
        if (!member.getJobPositions().isEmpty()) {
            JobPosition firstPosition = member.getJobPositions().get(0).getJobPosition();
            this.representativePosition = firstPosition.getName();
            this.representativePositionEn = firstPosition.getCode().getEnglishName();
        }

        // 보유 기술 전체 (직군 종속 필터 없이 회원의 모든 기술을 displayOrder 순으로)
        this.skills = member.getMemberTechStacks().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(memberTechStack -> memberTechStack.getTechStack().getName())
                .toList();
        this.isParticipating = member.getIsParticipating();
        this.projectCount = member.getProjectMembers().size();
        this.introduce = member.getIntroduction();
    }
}
