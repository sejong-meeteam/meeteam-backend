package com.wardk.meeteam_backend.domain.application.entity;


import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 지원서 엔티티.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "project_member_application")
public class ProjectApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_application_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    @Column(length = 800)
    private String motivation;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Builder
    private ProjectApplication(Project project, Member applicant, JobPosition jobPosition, String motivation) {
        this.project = project;
        this.applicant = applicant;
        this.jobPosition = jobPosition;
        this.motivation = motivation;
        this.status = ApplicationStatus.PENDING;
    }

    public static ProjectApplication create(Project project, Member applicant, JobPosition jobPosition, String motivation) {
        return ProjectApplication.builder()
                .project(project)
                .applicant(applicant)
                .jobPosition(jobPosition)
                .motivation(motivation)
                .build();
    }

    public void updateStatus(ApplicationStatus status) {
        this.status = status;
    }

    /**
     * 지원서를 거절 처리합니다.
     */
    public void reject() {
        this.status = ApplicationStatus.REJECTED;
    }

    /**
     * 대기 중인 지원서인지 확인합니다.
     */
    public boolean isPending() {
        return this.status == ApplicationStatus.PENDING;
    }

    /**
     * 지원서를 취소 처리합니다.
     * PENDING 상태에서만 취소 가능합니다.
     */
    public void cancel() {
        this.status = ApplicationStatus.CANCELLED;
    }

    /**
     * 지원자 본인인지 확인합니다.
     */
    public boolean isApplicant(Long memberId) {
        return this.applicant.getId().equals(memberId);
    }
}
