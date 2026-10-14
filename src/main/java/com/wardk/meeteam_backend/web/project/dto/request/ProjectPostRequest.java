package com.wardk.meeteam_backend.web.project.dto.request;


import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.project.entity.PlatformCategory;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCategory;
import com.wardk.meeteam_backend.domain.project.entity.RecruitmentDeadlineType;
import com.wardk.meeteam_backend.domain.project.service.dto.ProjectPostCommand;
import com.wardk.meeteam_backend.domain.project.service.dto.RecruitmentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.wardk.meeteam_backend.global.constant.PatternConstants.COMMUNICATION_CHANNEL_URL_PATTERN;
import static com.wardk.meeteam_backend.global.constant.PatternConstants.GITHUB_REPOSITORY_URL_PATTERN;

@Data
public class ProjectPostRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Schema(description = "프로젝트 제목", example = "test")
    private String projectName;

    @Schema(description = "Github 레포 주소", example = "https://github.com/username/repository")
    @Pattern(regexp = GITHUB_REPOSITORY_URL_PATTERN)
    private String githubRepositoryUrl;

    @Schema(description = "소통 채널 링크 (디코, 슬랙, 카카오톡 오픈 채팅)", example = "https://discord.gg/abc123")
    @Pattern(regexp = COMMUNICATION_CHANNEL_URL_PATTERN)
    private String communicationChannelUrl;

    @NotNull(message = "카테고리를 선택해주세요.")
    @Schema(description = "프로젝트 카테고리", example = "CAPSTONE")
    private ProjectCategory projectCategory;

    @Size(max = 5000, message = "설명은 최대 5,000자까지 입력 가능합니다.")
    @Schema(description = "프로젝트 설명", example = "test description")
    private String description;

    @NotNull(message = "출시 플랫폼을 선택해주세요.")
    @Schema(description = "출시 플랫폼 카테고리", example = "WEB")
    private PlatformCategory platformCategory;

    @NotNull(message = "프로젝트 생성자의 직무 포지션 코드를 입력해주세요")
    @Schema(description = "생성자 직무 포지션 코드", example = "JAVA_SPRING")
    private JobPositionCode creatorJobPositionCode;

    @NotEmpty(message = "최소 한 개 이상의 모집 분야를 입력해주세요.")
    @Valid
    @Schema(
            description = "모집분야 리스트 (/api/jobs/options 에서 직군/직무/기술스택 정보 조회)",
            example = "[{\"jobFieldCode\": \"BACKEND\", \"jobPositionCode\": \"JAVA_SPRING\", \"recruitmentCount\": 2, \"techStackIds\": [30, 31, 38]}]"
    )
    private List<ProjectRecruitRequest> recruitments = new ArrayList<>();

    @NotNull(message = "프로젝트 마감 방식을 선택해주세요. ex) END_DATE(마감 날자 방식), RECRUITMENT_COMPLETED(모집 완료 시)")
    @Schema(description = "프로젝트 마감 방식", example = "END_DATE")
    private RecruitmentDeadlineType recruitmentDeadlineType;

    @Schema(description = "프로젝트 마감일")
    private LocalDate endDate;

    /**
     * 웹 요청 DTO를 도메인 커맨드 DTO로 변환합니다.
     */
    public ProjectPostCommand toCommand() {
        List<RecruitmentCommand> recruitmentCommands = this.recruitments.stream()
                .map(ProjectRecruitRequest::toCommand)
                .toList();

        return new ProjectPostCommand(
                this.projectName,
                this.githubRepositoryUrl,
                this.communicationChannelUrl,
                this.projectCategory,
                this.description,
                this.platformCategory,
                this.creatorJobPositionCode,
                recruitmentCommands,
                this.recruitmentDeadlineType,
                this.endDate
        );
    }
}
