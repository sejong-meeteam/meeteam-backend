package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.skill.entity.Skill;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ProjectListResponse {

    private Long projectId;
    private String name;
    private String imageUrl;
    private String creatorName;
    private List<String> skills;
    private LocalDate startDate;

    public static ProjectListResponse responseDto(Project project) {
        return ProjectListResponse.builder()
                .projectId(project.getId())
                .name(project.getName())
                .imageUrl(project.getImageUrl())
                .creatorName(project.getCreator().getRealName())
                .skills(project.getProjectSkills().stream()
                        .map(ps -> ps.getSkill().getSkillName()).toList())
                .startDate(project.getStartDate())
                .build();
    }
}
