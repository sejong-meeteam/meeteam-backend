package com.wardk.meeteam_backend.web.mainpage.dto.response;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MainPageProjectResponse { // 프로젝트 카드 정보
    private String id; // JavaScript 정밀도 문제 해결을 위해 String 사용
    private String name;
    private String description;
    private String projectCategory;
    private String platformCategory;
    private String imageUrl;
    private String creatorName;
    private LocalDateTime createdDate;
    private LocalDate endDate;
    private Integer likes;
    private List<String> skillNames;
    private List<RecruitmentInfoResponse> recruitmentInfo;

    public static MainPageProjectResponse responseDto(Project project) {
        return MainPageProjectResponse.builder()
                .id(String.valueOf(project.getId()))
                .name(project.getName())
                .description(project.getDescription())
                .projectCategory(project.getProjectCategory() != null ? project.getProjectCategory().name() : null)
                .platformCategory(project.getPlatformCategory() != null ? project.getPlatformCategory().name() : null)
                .imageUrl(project.getImageUrl())
                .creatorName(project.getCreator() != null ? project.getCreator().getRealName() : null)
                .createdDate(project.getCreatedAt())
                .endDate(project.getEndDate())
                .likes(project.getLikeCount())
                .skillNames(
                        project.getProjectSkills() != null
                                ? project.getProjectSkills().stream()
                                .filter(ps -> ps != null && ps.getSkill() != null && ps.getSkill().getSkillName() != null)
                                .map(ps -> ps.getSkill().getSkillName())
                                .distinct() // 중복제거
                                .collect(Collectors.toList())
                                : List.of()
                )
                .recruitmentInfo(
                        project.getRecruitments() != null
                                ? project.getRecruitments().stream()
                                .filter(r -> r != null && r.getJobPosition() != null)  // null 필터링
                                .map(RecruitmentInfoResponse::responseDto)
                                .collect(Collectors.toList())
                                : List.of()
                )
                .build();
    }
}