package com.wardk.meeteam_backend.domain.project.service;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import com.wardk.meeteam_backend.domain.application.entity.ProjectApplication;
import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.file.service.S3FileService;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.entity.TechStack;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.job.repository.TechStackRepository;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.project.service.dto.ProjectEditCommand;
import com.wardk.meeteam_backend.domain.project.service.dto.RecruitmentEditCommand;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentTechStack;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectEditPrefillResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectEditResponse;
import com.wardk.meeteam_backend.web.project.dto.response.RecruitmentEditInfo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 프로젝트 수정 서비스 구현체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectEditServiceImpl implements ProjectEditService {

    private final S3FileService s3FileService;
    private final ProjectRepository projectRepository;
    private final MemberRepository memberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectApplicationRepository projectApplicationRepository;
    private final JobPositionRepository jobPositionRepository;
    private final TechStackRepository techStackRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public ProjectEditPrefillResponse getEditPrefill(Long projectId, String requesterEmail) {
        // 1단계: 엔티티 조회
        Project project = projectRepository.findProjectForEdit(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        // 2단계: 권한 검증
        project.validateLeaderPermission(requesterEmail);

        // 3단계: 리더 정보 조회
        ProjectMember leader = projectMemberRepository.findLeaderByProjectId(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        // 4단계: 포지션별 대기 지원자 수 조회
        Map<Long, Long> pendingCountByPositionId = projectApplicationRepository
                .countPendingByProjectIdGroupByJobPositionId(projectId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        // 5단계: 모집 포지션 정보 생성
        List<RecruitmentEditInfo> recruitmentInfos = project.getRecruitments().stream()
                .map(recruitment -> {
                    Long positionId = recruitment.getJobPosition().getId();
                    int pendingCount = pendingCountByPositionId.getOrDefault(positionId, 0L).intValue();
                    return RecruitmentEditInfo.from(recruitment, pendingCount);
                })
                .toList();

        return ProjectEditPrefillResponse.from(project, leader, recruitmentInfos);
    }

    @CacheEvict(value = "mainPageProjects", allEntries = true)
    @Override
    public ProjectEditResponse update(Long projectId, ProjectEditCommand command, MultipartFile file, String requesterEmail) {
        // 1단계: 엔티티 조회
        Project project = projectRepository.findProjectForEdit(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        // 2단계: 권한 검증
        project.validateLeaderPermission(requesterEmail);
        project.validateEditable();

        // 3단계: 이미지 업로드
        Member creator = findMemberByEmail(requesterEmail);
        String imageUrl = uploadProjectImage(file, creator.getId());

        // 4단계: 기본 정보 업데이트
        project.updateBasicInfo(
                command.name(),
                command.description(),
                command.projectCategory(),
                command.platformCategory(),
                command.githubRepositoryUrl(),
                command.communicationChannelUrl(),
                command.endDate(),
                command.recruitmentDeadlineType(),
                imageUrl
        );

        // 5단계: 모집 포지션 업데이트 및 자동 거절 처리
        int autoRejectedCount = updateRecruitments(project, command);

        // 6단계: 프로젝트 모집 상태 자동 업데이트
        project.updateRecruitmentStatusBasedOnPositions();

        projectRepository.save(project);

        log.info("프로젝트 수정 완료 - projectId: {}, requesterEmail: {}", projectId, requesterEmail);

        return ProjectEditResponse.from(project, autoRejectedCount);
    }

    private int updateRecruitments(Project project, ProjectEditCommand command) {
        int autoRejectedCount = 0;

        // 현재 모집 포지션 맵 생성 (recruitmentStateId -> RecruitmentState)
        Map<Long, RecruitmentState> currentRecruitmentMap = project.getRecruitments().stream()
                .collect(Collectors.toMap(RecruitmentState::getId, r -> r));

        // 요청에 포함된 모집 포지션 ID 목록
        Set<Long> requestedRecruitmentIds = command.recruitments().stream()
                .filter(r -> r.recruitmentStateId() != null)
                .map(RecruitmentEditCommand::recruitmentStateId)
                .collect(Collectors.toSet());

        // 삭제할 포지션 처리
        List<RecruitmentState> toRemove = new ArrayList<>();
        for (RecruitmentState recruitment : project.getRecruitments()) {
            if (!requestedRecruitmentIds.contains(recruitment.getId())) {
                // 승인 인원이 있으면 삭제 불가
                if (recruitment.getCurrentCount() > 0) {
                    throw new CustomException(ErrorCode.RECRUITMENT_HAS_APPROVED_MEMBERS);
                }

                // 대기 지원자 확인
                List<ProjectApplication> pendingApplications = projectApplicationRepository
                        .findPendingByProjectIdAndJobPositionId(project.getId(), recruitment.getJobPosition().getId());

                if (!pendingApplications.isEmpty()) {
                    // 대기 지원자가 있고 확인 플래그가 없으면 에러
                    if (!command.confirmDeletePositionsWithPendingApplicants()) {
                        throw new CustomException(ErrorCode.RECRUITMENT_HAS_PENDING_APPLICANTS);
                    }
                    // 대기 지원자 자동 거절 처리
                    for (ProjectApplication application : pendingApplications) {
                        application.updateStatus(ApplicationStatus.REJECTED);
                        autoRejectedCount++;
                    }
                }

                toRemove.add(recruitment);
            }
        }

        // 삭제 처리
        project.getRecruitments().removeAll(toRemove);

        // 기존 포지션 수정 및 신규 포지션 추가
        for (RecruitmentEditCommand recruitmentCmd : command.recruitments()) {
            if (recruitmentCmd.recruitmentStateId() != null) {
                // 기존 포지션 수정
                RecruitmentState recruitment = currentRecruitmentMap.get(recruitmentCmd.recruitmentStateId());
                if (recruitment != null) {
                    // 모집 인원 축소 검증
                    if (recruitmentCmd.recruitmentCount() < recruitment.getCurrentCount()) {
                        throw new CustomException(ErrorCode.RECRUITMENT_COUNT_BELOW_CURRENT);
                    }

                    // 모집 인원 업데이트
                    recruitment.updateRecruitmentCount(recruitmentCmd.recruitmentCount());

                    // 기술 스택 업데이트 (기존 삭제 후 flush, 새로운 스택 추가)
                    recruitment.clearTechStacks();
                    entityManager.flush();

                    List<TechStack> techStacks = techStackRepository.findByIdIn(recruitmentCmd.techStackIds());
                    for (TechStack techStack : techStacks) {
                        recruitment.addRecruitmentTechStack(RecruitmentTechStack.create(techStack));
                    }

                    // 포지션 상태 업데이트 (인원 변경으로 인한 마감/재오픈)
                    if (recruitment.getCurrentCount() >= recruitment.getRecruitmentCount()) {
                        recruitment.close();
                    } else {
                        recruitment.reopen();
                    }
                }
            } else {
                // 신규 포지션 추가
                JobPosition jobPosition = jobPositionRepository.findByCode(recruitmentCmd.jobPositionCode())
                        .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSITION_NOT_FOUND));

                RecruitmentState newRecruitment = RecruitmentState.createRecruitmentState(
                        jobPosition, recruitmentCmd.recruitmentCount());

                // 기술 스택 추가
                List<TechStack> techStacks = techStackRepository.findByIdIn(recruitmentCmd.techStackIds());
                for (TechStack techStack : techStacks) {
                    newRecruitment.addRecruitmentTechStack(RecruitmentTechStack.create(techStack));
                }

                project.addRecruitment(newRecruitment);
            }
        }

        return autoRejectedCount;
    }

    private Member findMemberByEmail(String email) {
        // 소프트 삭제(deletedAt) 회원이 같은 이메일로 남아있을 수 있어 활성 회원만 조회 (NonUniqueResult 방지)
        return memberRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private String uploadProjectImage(MultipartFile file, Long uploaderId) {
        if (file != null && !file.isEmpty()) {
            return s3FileService.uploadFile(file, "images", uploaderId);
        }
        return null;
    }
}