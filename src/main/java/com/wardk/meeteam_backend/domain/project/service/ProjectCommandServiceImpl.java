package com.wardk.meeteam_backend.domain.project.service;

import com.wardk.meeteam_backend.domain.file.service.S3FileService;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.ProjectEndEvent;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.project.service.dto.ProjectPostCommand;
import com.wardk.meeteam_backend.domain.project.vo.RecruitmentDeadline;
import com.wardk.meeteam_backend.domain.projectmember.service.ProjectMemberService;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.recruitment.service.RecruitmentDomainService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectDeleteResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectPostResponse;
import io.micrometer.core.annotation.Counted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 생성/삭제 서비스 구현체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectCommandServiceImpl implements ProjectCommandService {

    private final S3FileService s3FileService;
    private final ProjectRepository projectRepository;
    private final MemberRepository memberRepository;
    private final JobPositionRepository jobPositionRepository;
    private final ProjectMemberService projectMemberService;
    private final RecruitmentDomainService recruitmentDomainService;
    private final NotificationSaveService notificationSaveService;
    private final ApplicationEventPublisher eventPublisher;

    @CacheEvict(value = "mainPageProjects", allEntries = true)
    @Counted("project.create")
    @Override
    public ProjectPostResponse create(ProjectPostCommand command, MultipartFile file, String requesterEmail) {
        Member creator = findMemberByEmail(requesterEmail);
        String imageUrl = uploadProjectImage(file, creator.getId());

        // 값 객체로 마감 정책 검증 캡슐화
        RecruitmentDeadline deadline = new RecruitmentDeadline(
                command.recruitmentDeadlineType(),
                command.endDate()
        );

        // 프로젝트 생성
        Project project = Project.create(command, creator, deadline, imageUrl);

        // 모집 정보 생성 및 추가 (도메인 서비스 위임)
        List<RecruitmentState> recruitmentStates = recruitmentDomainService.createRecruitmentStates(command.recruitments());
        recruitmentStates.forEach(project::addRecruitment);

        projectRepository.save(project);

        // 생성자를 프로젝트 멤버로 추가
        addCreatorAsProjectMember(project, creator, command.creatorJobPositionCode());

        log.info("프로젝트 생성 완료 - projectId: {}, creatorEmail: {}", project.getId(), requesterEmail);

        return ProjectPostResponse.from(project);
    }

    @CacheEvict(value = "mainPageProjects", allEntries = true)
    @Override
    public ProjectDeleteResponse delete(Long projectId, String requesterEmail) {
        // 1단계: 엔티티 조회
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        // 2단계: 권한 검증
        project.validateLeaderPermission(requesterEmail);

        // 3단계: 대기 중인 지원서 자동 거절 처리
        project.rejectAllPendingApplications();

        // 4단계: 삭제 처리
        project.delete();

        // 5단계: 알림 처리
        List<Member> members = project.getMembers().stream()
                .map(pm -> pm.getMember())
                .toList();

        notificationSaveService.saveForProjectEnd(project, members);

        List<Long> membersId = members.stream()
                .map(Member::getId)
                .toList();

        eventPublisher.publishEvent(new ProjectEndEvent(
                NotificationType.PROJECT_END,
                membersId,
                projectId,
                project.getName(),
                LocalDate.now()
        ));

        log.info("프로젝트 삭제 완료 - projectId: {}, requesterEmail: {}", projectId, requesterEmail);

        return ProjectDeleteResponse.responseDto(projectId, project.getName());
    }

    private Member findMemberByEmail(String email) {
        // 소프트 삭제(deletedAt) 회원이 같은 이메일로 남아있을 수 있어(uk: email+deleted_at),
        // 활성 회원만 조회해야 NonUniqueResultException(2 results)이 발생하지 않는다.
        return memberRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private String uploadProjectImage(MultipartFile file, Long uploaderId) {
        if (file != null && !file.isEmpty()) {
            return s3FileService.uploadFile(file, "images", uploaderId);
        }
        return null;
    }

    private void addCreatorAsProjectMember(Project project, Member creator,
            com.wardk.meeteam_backend.domain.job.entity.JobPositionCode creatorJobPositionCode) {
        JobPosition creatorPosition = jobPositionRepository
                .findByCode(creatorJobPositionCode)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSITION_NOT_FOUND));
        projectMemberService.addCreator(project.getId(), creator.getId(), creatorPosition);
    }
}