package com.wardk.meeteam_backend.domain.proposal.entity;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A leader's invitation for a member to look at one recruiting project and position. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "project_proposal", indexes = {
        @Index(name = "idx_proposal_proposer_created", columnList = "proposer_id, created_at"),
        @Index(name = "idx_proposal_member", columnList = "member_id, status")
})
public class ProjectProposal extends BaseEntity {

    public static final int MESSAGE_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** The project's leader at the time of sending. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposer_id", nullable = false)
    private Member proposer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member receiver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    @Column(nullable = false, length = MESSAGE_MAX_LENGTH)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalStatus status;

    private ProjectProposal(Project project, Member proposer, Member receiver, JobPosition jobPosition, String message) {
        this.project = project;
        this.proposer = proposer;
        this.receiver = receiver;
        this.jobPosition = jobPosition;
        this.message = message;
        this.status = ProposalStatus.PENDING;
    }

    public static ProjectProposal pending(Project project, Member proposer, Member receiver,
                                          JobPosition jobPosition, String message) {
        return new ProjectProposal(project, proposer, receiver, jobPosition, message);
    }
}
