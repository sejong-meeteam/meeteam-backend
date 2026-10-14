package com.wardk.meeteam_backend.domain.project.entity;

import com.wardk.meeteam_backend.domain.skill.entity.Skill;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSkill {//프로젝트_기술_스택_테이블(중간테이블)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_skill_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Builder
    public ProjectSkill(Skill skill) {
        this.skill = skill;
    }

    public static ProjectSkill createProjectSkill(Skill skill) {
        return ProjectSkill.builder()
                .skill(skill)
                .build();
    }

    public static ProjectSkill create(Project project, Skill skill) {
        ProjectSkill ps = new ProjectSkill(skill);
        ps.assignProject(project);
        return ps;
    }

    public void assignProject(Project project) {
        this.project = project;
    }
}
