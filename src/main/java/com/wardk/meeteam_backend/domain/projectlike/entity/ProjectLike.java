package com.wardk.meeteam_backend.domain.projectlike.entity;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "project_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectLike {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    private ProjectLike(Member member, Project project) {
        this.member = member;
        this.project = project;
    }


    public static ProjectLike create(Member member, Project project) {
        return new ProjectLike(member, project);
    }

}
