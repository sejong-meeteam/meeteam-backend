package com.wardk.meeteam_backend.web.application.dto.response;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 프로젝트 지원 페이지 응답 DTO.
 */
@Schema(description = "프로젝트 지원 페이지 정보")
@Getter
@Builder
public class ApplicationPageResponse {

    @Schema(description = "지원자 정보")
    private final ApplicantInfo applicant;

    @Schema(description = "모집 포지션 목록")
    private final List<RecruitmentInfo> recruitments;

    /**
     * 지원자 정보.
     */
    @Schema(description = "지원자 정보")
    @Getter
    @Builder
    public static class ApplicantInfo {

        @Schema(description = "프로필 이미지 URL")
        private final String profileImageUrl;

        @Schema(description = "이름")
        private final String name;

        @Schema(description = "직군명 목록")
        private final List<String> jobFieldNames;

        @Schema(description = "직무명 목록")
        private final List<String> jobPositionNames;

        @Schema(description = "나이")
        private final Integer age;

        @Schema(description = "성별")
        private final String gender;

        @Schema(description = "이메일")
        private final String email;

        @Schema(description = "기술스택 목록 (displayOrder 순)")
        private final List<TechStackInfo> techStacks;

        @Schema(description = "프로필 요약 (직군 / 기술스택)")
        private final String profileSummary;

        public static ApplicantInfo from(Member member) {
            List<String> jobFieldNames = member.getJobPositions().stream()
                    .map(mjp -> mjp.getJobPosition().getJobField().getName())
                    .distinct()
                    .toList();

            List<String> jobPositionNames = member.getJobPositions().stream()
                    .map(mjp -> mjp.getJobPosition().getName())
                    .toList();

            // 기술스택 목록 (displayOrder 순)
            List<TechStackInfo> techStacks = member.getMemberTechStacks().stream()
                    .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                    .map(mts -> new TechStackInfo(
                            mts.getTechStack().getId(),
                            mts.getTechStack().getName(),
                            mts.getDisplayOrder()
                    ))
                    .toList();

            // 프로필 요약: 첫 번째 직군 / 첫 번째 기술스택
            String firstJobField = jobFieldNames.isEmpty() ? null : jobFieldNames.get(0);
            String firstTechStack = techStacks.isEmpty() ? null : techStacks.get(0).name();

            String profileSummary = null;
            if (firstJobField != null && firstTechStack != null) {
                profileSummary = firstJobField + " / " + firstTechStack;
            } else if (firstJobField != null) {
                profileSummary = firstJobField;
            }

            String genderStr = member.getGender() != null ? member.getGender().name() : null;
            if (genderStr != null) {
                genderStr = switch (genderStr) {
                    case "MALE" -> "남성";
                    case "FEMALE" -> "여성";
                    default -> genderStr;
                };
            }

            return ApplicantInfo.builder()
                    .profileImageUrl(member.getStoreFileName())
                    .name(member.getRealName())
                    .jobFieldNames(jobFieldNames)
                    .jobPositionNames(jobPositionNames)
                    .age(member.getAge())
                    .gender(genderStr)
                    .email(member.getEmail())
                    .techStacks(techStacks)
                    .profileSummary(profileSummary)
                    .build();
        }
    }

    /**
     * 기술스택 정보.
     */
    @Schema(description = "기술스택 정보")
    public record TechStackInfo(
            @Schema(description = "기술스택 ID")
            Long id,

            @Schema(description = "기술스택명")
            String name,

            @Schema(description = "표시 순서")
            Integer displayOrder
    ) {
    }

    /**
     * 모집 포지션 정보.
     */
    @Schema(description = "모집 포지션 정보")
    @Getter
    @Builder
    public static class RecruitmentInfo {

        @Schema(description = "모집 상태 ID")
        private final Long id;

        @Schema(description = "직군명")
        private final String jobFieldName;

        @Schema(description = "직무명")
        private final String jobPositionName;

        @Schema(description = "필요 기술스택 목록")
        private final List<String> techStacks;

        @Schema(description = "모집 마감 여부")
        private final boolean isClosed;

        public static RecruitmentInfo from(RecruitmentState recruitment) {
            List<String> techStacks = recruitment.getRecruitmentTechStacks().stream()
                    .map(rts -> rts.getTechStack().getName())
                    .toList();

            return RecruitmentInfo.builder()
                    .id(recruitment.getId())
                    .jobFieldName(recruitment.getJobField().getName())
                    .jobPositionName(recruitment.getJobPosition().getName())
                    .techStacks(techStacks)
                    .isClosed(recruitment.isClosed())
                    .build();
        }
    }

    public static ApplicationPageResponse of(Member applicant, List<RecruitmentState> recruitments) {
        ApplicantInfo applicantInfo = ApplicantInfo.from(applicant);

        List<RecruitmentInfo> recruitmentInfos = recruitments.stream()
                .map(RecruitmentInfo::from)
                .toList();

        return ApplicationPageResponse.builder()
                .applicant(applicantInfo)
                .recruitments(recruitmentInfos)
                .build();
    }
}