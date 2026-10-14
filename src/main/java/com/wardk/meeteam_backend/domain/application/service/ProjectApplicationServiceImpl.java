package com.wardk.meeteam_backend.domain.application.service;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.NotificationEvent;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.projectmember.service.ProjectMemberService;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.recruitment.repository.RecruitmentStateRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.application.dto.request.*;
import com.wardk.meeteam_backend.web.application.dto.response.*;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import io.micrometer.core.annotation.Counted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 프로젝트 지원 서비스 구현체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectApplicationServiceImpl implements ProjectApplicationService {

    private final ProjectRepository projectRepository;
    private final MemberRepository memberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectApplicationRepository applicationRepository;
    private final ProjectMemberService projectMemberService;
    private final JobPositionRepository jobPositionRepository;
    private final RecruitmentStateRepository recruitmentStateRepository;
    private final NotificationSaveService notificationSaveService;
    private final ApplicationEventPublisher eventPublisher;

    @Counted("project.apply")
    @Override
    public ApplicationResponse apply(Long projectId, Long memberId, ApplicationRequest request) {
        // 1단계: 엔티티 조회
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        JobPosition jobPosition = jobPositionRepository.findByCode(request.jobPositionCode())
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSITION_NOT_FOUND));

        // 2단계: 권한 검증
        validateNotSelfApplication(project, memberId);

        // 3단계: 비즈니스 규칙 검증
        validateApplyPrecondition(project, member, projectId, memberId);
        validateRecruitmentPosition(projectId, jobPosition);

        // 4단계: 지원서 생성 및 저장
        ProjectApplication savedApplication = createAndSaveApplication(project, member, jobPosition, request.motivation());

        // 5단계: 알림 발행 및 응답
        publishApplyNotifications(project, member, savedApplication);

        log.info("프로젝트 지원 완료 - projectId: {}, applicantId: {}, jobPositionCode: {}",
                projectId, memberId, request.jobPositionCode());

        return ApplicationResponse.from(savedApplication);
    }

    private void validateNotSelfApplication(Project project, Long memberId) {
        if (project.getCreator().getId().equals(memberId)) {
            throw new CustomException(ErrorCode.APPLICATION_SELF_PROJECT_FORBIDDEN);
        }
    }

    private void validateApplyPrecondition(Project project, Member member, Long projectId, Long memberId) {
        if (project.isCompleted()) {
            throw new CustomException(ErrorCode.PROJECT_RECRUITMENT_SUSPENDED);
        }

        if (applicationRepository.existsByProjectAndApplicant(project, member)) {
            throw new CustomException(ErrorCode.PROJECT_APPLICATION_ALREADY_EXISTS);
        }

        if (projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
            throw new CustomException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }
    }

    private void validateRecruitmentPosition(Long projectId, JobPosition jobPosition) {
        RecruitmentState recruitmentState = recruitmentStateRepository.findByProjectIdAndJobPosition(projectId, jobPosition)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_POSITION_NOT_RECRUITING));

        if (recruitmentState.isClosed()) {
            throw new CustomException(ErrorCode.RECRUITMENT_POSITION_CLOSED);
        }
    }

    private ProjectApplication createAndSaveApplication(Project project, Member member, JobPosition jobPosition, String motivation) {
        ProjectApplication application = ProjectApplication.create(
                project,
                member,
                jobPosition,
                motivation
        );

        return applicationRepository.save(application);
    }

    private void publishApplyNotifications(Project project, Member member, ProjectApplication savedApplication) {
        Long receiverId = project.getCreator().getId();
        Long actorId = member.getId();

        notificationSaveService.saveForApply(
                project.getCreator(), member, project, savedApplication.getId()
        );

        eventPublisher.publishEvent(new NotificationEvent(
                receiverId,
                project.getId(),
                actorId,
                NotificationType.PROJECT_APPLY,
                savedApplication.getId()
        ));

        eventPublisher.publishEvent(new NotificationEvent(
                actorId,
                project.getId(),
                actorId,
                NotificationType.PROJECT_MY_APPLY
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectApplicationListResponse> getApplicationList(Long projectId, String requesterEmail) {
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        if (!project.getCreator().getEmail().equals(requesterEmail)) {
            throw new CustomException(ErrorCode.PROJECT_MEMBER_FORBIDDEN);
        }

        List<RecruitmentState> recruitmentStates = recruitmentStateRepository.findByProjectIdWithJobPosition(projectId);

        return applicationRepository.findPendingByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(application -> {
                    RecruitmentState recruitmentState = recruitmentStates.stream()
                            .filter(rs -> rs.getJobPosition().getId().equals(application.getJobPosition().getId()))
                            .findFirst()
                            .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));
                    return ProjectApplicationListResponse.from(application, recruitmentState);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationDetailResponse getApplicationDetail(Long projectId, Long applicationId, String requesterEmail) {
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        if (!project.getCreator().getEmail().equals(requesterEmail)) {
            throw new CustomException(ErrorCode.PROJECT_MEMBER_FORBIDDEN);
        }

        ProjectApplication application = applicationRepository.findByIdWithApplicantAndJobPosition(projectId, applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        return ApplicationDetailResponse.from(application);
    }

    @Override
    public ApplicationDecisionResponse decide(Long applicationId, ApplicationDecisionRequest request, String requesterEmail) {
        // 1단계: 엔티티 조회
        ProjectApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        // 2단계: 권한 검증
        validateProjectCreator(application, requesterEmail);

        // 3단계: 비즈니스 규칙 검증
        validateDecisionPrecondition(application);

        // 4단계: 결정 처리
        return processDecision(application, request.decision());
    }

    private void validateProjectCreator(ProjectApplication application, String requesterEmail) {
        if (!application.getProject().getCreator().getEmail().equals(requesterEmail)) {
            throw new CustomException(ErrorCode.PROJECT_MEMBER_FORBIDDEN);
        }
    }

    private void validateDecisionPrecondition(ProjectApplication application) {
        if (application.getProject().isCompleted()) {
            throw new CustomException(ErrorCode.PROJECT_ALREADY_COMPLETED);
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new CustomException(ErrorCode.APPLICATION_ALREADY_DECIDED);
        }
    }

    private ApplicationDecisionResponse processDecision(ProjectApplication application, ApplicationStatus decision) {
        application.updateStatus(decision);

        if (decision == ApplicationStatus.ACCEPTED) {
            return processAcceptance(application);
        }
        return processRejection(application);
    }

    private ApplicationDecisionResponse processAcceptance(ProjectApplication application) {
        Long projectId = application.getProject().getId();
        Long applicantId = application.getApplicant().getId();
        Long projectCreatorId = application.getProject().getCreator().getId();

        projectMemberService.addMember(
                projectId,
                applicantId,
                application.getJobPosition()
        );

        notificationSaveService.saveForApprove(
                application.getApplicant(),
                application.getProject(),
                projectCreatorId,
                application.getId()
        );

        eventPublisher.publishEvent(new NotificationEvent(
                applicantId,
                projectId,
                projectCreatorId,
                NotificationType.PROJECT_APPROVE
        ));

        return ApplicationDecisionResponse.acceptResponseDto(
                application.getId(),
                application.getProject().getId(),
                application.getApplicant().getId(),
                application.getStatus()
        );
    }

    private ApplicationDecisionResponse processRejection(ProjectApplication application) {
        Long projectId = application.getProject().getId();
        Long applicantId = application.getApplicant().getId();
        Long projectCreatorId = application.getProject().getCreator().getId();

        notificationSaveService.saveForReject(
                application.getApplicant(),
                application.getProject(),
                projectCreatorId,
                application.getId()
        );

        eventPublisher.publishEvent(new NotificationEvent(
                applicantId,
                projectId,
                projectCreatorId,
                NotificationType.PROJECT_REJECT
        ));

        return ApplicationDecisionResponse.rejectResponseDto(
                application.getId(),
                application.getStatus()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppliedProjectResponse> getAppliedProjects(CustomSecurityUserDetails userDetails) {
        List<ProjectApplication> applications = applicationRepository.findAllByApplicantId(userDetails.getMemberId());

        return applications.stream()
                .map(AppliedProjectResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationPageResponse getApplicationPage(Long projectId, Long memberId) {
        // 1단계: 엔티티 조회
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 2단계: 프로젝트 리더 접근 차단
        if (project.getCreator().getId().equals(memberId)) {
            throw new CustomException(ErrorCode.APPLICATION_SELF_PROJECT_FORBIDDEN);
        }

        // 3단계: 이미 프로젝트 멤버인 경우 접근 차단
        if (projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
            throw new CustomException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }

        // 4단계: 모집 중인 포지션만 조회 (마감된 포지션 제외)
        List<RecruitmentState> recruitments = recruitmentStateRepository.findByProjectIdWithJobPosition(projectId)
                .stream()
                .filter(r -> !r.isClosed())
                .toList();

        return ApplicationPageResponse.of(member, recruitments);
    }

    @Override
    public ApplicationCancelResponse cancel(Long applicationId, Long memberId) {
        // 1단계: 지원서 조회
        ProjectApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        // 2단계: 본인 확인
        if (!application.isApplicant(memberId)) {
            throw new CustomException(ErrorCode.APPLICATION_CANCEL_FORBIDDEN);
        }

        // 3단계: PENDING 상태 확인
        if (!application.isPending()) {
            throw new CustomException(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED);
        }

        // 4단계: 취소 처리
        application.cancel();

        log.info("지원 취소 완료 - applicationId: {}, memberId: {}", applicationId, memberId);

        return ApplicationCancelResponse.of(
                application.getId(),
                application.getProject().getId(),
                application.getProject().getName()
        );
    }
}
