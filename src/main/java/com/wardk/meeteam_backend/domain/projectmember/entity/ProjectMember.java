package com.wardk.meeteam_backend.domain.projectmember.entity;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "project_member")
public class ProjectMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_position_id")
    private JobPosition jobPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_member_role")
    private ProjectMemberRole role;

    public void assignProject(Project project) {
        this.project = project;
    }

    @Builder
    public ProjectMember(Member member, JobPosition jobPosition, ProjectMemberRole role) {
        this.member = member;
        this.jobPosition = jobPosition;
        this.role = role;
    }

    public static ProjectMember createProjectMember(Member member, JobPosition jobPosition, ProjectMemberRole role) {
        return ProjectMember.builder()
                .member(member)
                .jobPosition(jobPosition)
                .role(role)
                .build();
    }
}