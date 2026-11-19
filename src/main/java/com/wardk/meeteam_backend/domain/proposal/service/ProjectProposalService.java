package com.wardk.meeteam_backend.domain.proposal.service;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.NotificationEvent;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.proposal.entity.ProjectProposal;
import com.wardk.meeteam_backend.domain.proposal.entity.ProposalStatus;
import com.wardk.meeteam_backend.domain.proposal.repository.ProjectProposalRepository;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.proposal.dto.request.ProjectProposalRequest;
import com.wardk.meeteam_backend.web.proposal.dto.response.ProjectProposalResponse;
import com.wardk.meeteam_backend.web.proposal.dto.response.ProposableProjectResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 프로젝트 합류 제안. 받는 사람의 부담을 줄이는 규칙은 모두 여기서 강제한다.
 * 검사 순서는 프론트 계약(docs: project-proposal-backend.md 4절)과 같다.
 */
@Slf4j
@Service
@Transactional
public class ProjectProposalService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @PersistenceContext
    private EntityManager entityManager;

    private final ProjectRepository projectRepository;
    private final MemberRepository memberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectProposalRepository proposalRepository;
    private final NotificationSaveService notificationSaveService;
    private final ApplicationEventPublisher eventPublisher;
    private final int dailyLimit;

    public ProjectProposalService(ProjectRepository projectRepository,
                                  MemberRepository memberRepository,
                                  ProjectMemberRepository projectMemberRepository,
                                  ProjectProposalRepository proposalRepository,
                                  NotificationSaveService notificationSaveService,
                                  ApplicationEventPublisher eventPublisher,
                                  @Value("${app.proposal.daily-limit:20}") int dailyLimit) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.proposalRepository = proposalRepository;
        this.notificationSaveService = notificationSaveService;
        this.eventPublisher = eventPublisher;
        this.dailyLimit = dailyLimit;
    }

    @Transactional(readOnly = true)
    public List<ProposableProjectResponse> getProposableProjects(Long leaderId, Long targetId) {
        Member leader = findActiveMember(leaderId);
        Member target = validateTarget(leader, targetId);

        LocalDate today = LocalDate.now(SEOUL);
        List<Project> candidates = projectRepository.findRecruitingByLeaderId(leader.getId()).stream()
                .filter(project -> project.isRecruitingOn(today))
                .toList();
        if (candidates.isEmpty()) return List.of();

        Set<Long> teamProjectIds = projectMemberRepository.findAllByMemberId(target.getId()).stream()
                .map(membership -> membership.getProject().getId())
                .collect(Collectors.toSet());
        Set<Long> pendingProjectIds = new HashSet<>(proposalRepository.findProjectIdsByReceiverAndStatus(
                target.getId(), ProposalStatus.PENDING, candidates.stream().map(Project::getId).toList()));

        return candidates.stream()
                .filter(project -> !project.isLeader(target.getId()) && !teamProjectIds.contains(project.getId()))
                .map(project -> ProposableProjectResponse.of(project, pendingProjectIds.contains(project.getId())))
                .toList();
    }

    public ProjectProposalResponse propose(Long projectId, Long proposerId, ProjectProposalRequest request) {
        // Serializes one leader's sends so the duplicate and daily-limit checks cannot race.
        Member proposer = lockActiveMember(proposerId);

        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        if (!project.isLeader(proposer.getId())) {
            throw new CustomException(ErrorCode.PROPOSAL_NOT_LEADER);
        }
        if (!project.isRecruitingOn(LocalDate.now(SEOUL))) {
            throw new CustomException(ErrorCode.PROPOSAL_PROJECT_NOT_RECRUITING);
        }

        Member target = validateTarget(proposer, request.memberId());
        if (isOnTeam(project, target)) {
            throw new CustomException(ErrorCode.PROPOSAL_ALREADY_MEMBER);
        }
        RecruitmentState recruitment = project.getOpenRecruitments().stream()
                .filter(open -> open.getJobPosition().getId().equals(request.jobPositionId()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PROPOSAL_POSITION_CLOSED));
        if (proposalRepository.existsByProjectIdAndReceiverIdAndStatus(project.getId(), target.getId(), ProposalStatus.PENDING)) {
            throw new CustomException(ErrorCode.PROPOSAL_DUPLICATE,
                    "이미 '%s' 프로젝트로 %s님에게 보낸 제안이 있어요.".formatted(project.getName(), target.getPublicName()));
        }
        if (proposalRepository.countByProposerIdAndCreatedAtAfter(proposer.getId(), LocalDateTime.now().minusDays(1)) >= dailyLimit) {
            throw new CustomException(ErrorCode.PROPOSAL_RATE_LIMITED,
                    "제안은 하루 %d건까지 보낼 수 있어요. 내일 다시 시도해 주세요.".formatted(dailyLimit));
        }

        ProjectProposal proposal = proposalRepository.save(
                ProjectProposal.pending(project, proposer, target, recruitment.getJobPosition(), request.message()));
        notificationSaveService.saveForProposal(proposal);
        eventPublisher.publishEvent(NotificationEvent.proposalReceived(
                target.getId(), project.getId(), proposer.getId(), proposal.getId()));

        log.info("프로젝트 합류 제안 완료 - projectId: {}, proposerId: {}, receiverId: {}",
                project.getId(), proposer.getId(), target.getId());
        return ProjectProposalResponse.from(proposal);
    }

    /** Shared by both endpoints: receiver exists and is active, is not me, and shares my verified school. */
    private Member validateTarget(Member sender, Long targetId) {
        Member target = findActiveMember(targetId);
        if (target.getId().equals(sender.getId())) {
            throw new CustomException(ErrorCode.PROPOSAL_SELF);
        }
        if (!sender.isSameUniversityAs(target)) {
            throw new CustomException(ErrorCode.PROPOSAL_DIFFERENT_UNIVERSITY);
        }
        return target;
    }

    private boolean isOnTeam(Project project, Member member) {
        return project.isLeader(member.getId())
                || projectMemberRepository.existsByProjectIdAndMemberId(project.getId(), member.getId());
    }

    private Member findActiveMember(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Member lockActiveMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        entityManager.refresh(member, LockModeType.PESSIMISTIC_WRITE);
        if (!member.isActive()) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }
}
