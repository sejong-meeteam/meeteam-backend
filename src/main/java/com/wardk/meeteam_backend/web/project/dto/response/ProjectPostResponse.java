package com.wardk.meeteam_backend.web.project.dto.response;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectPostResponse {

    private Long id;
    private String title;
    private LocalDateTime createdAt;

    public ProjectPostResponse(Project project) {
        this.id = project.getId();
        this.title = project.getName();
        this.createdAt = project.getCreatedAt();
    }

    public static ProjectPostResponse from(Project project) {
        return new ProjectPostResponse(project);
    }
}
