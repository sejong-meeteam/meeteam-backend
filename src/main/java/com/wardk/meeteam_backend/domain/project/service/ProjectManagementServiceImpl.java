package com.wardk.meeteam_backend.domain.project.service;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMemberRole;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.project.dto.response.MemberExpelResponse;
import com.wardk.meeteam_backend.web.project.dto.response.RecruitmentStatusResponse;
import com.wardk.meeteam_backend.web.project.dto.response.TeamManagementResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 프로젝트 관리(모집상태, 팀원관리) 서비스 구현체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectManagementServiceImpl implements ProjectManagementService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public RecruitmentStatusResponse toggleRecruitmentStatus(Long projectId, String requesterEmail) {
        // 1단계: 엔티티 조회
        Project project = projectRepository.findByIdWithRecruitment(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        // 2단계: 권한 검증
        project.validateLeaderPermission(requesterEmail);

        // 3단계: 상태 토글
        project.toggleRecruitmentStatus();
        projectRepository.save(project);

        log.info("모집 상태 토글 완료 - projectId: {}, newStatus: {}", projectId, project.getRecruitmentStatus());

        return RecruitmentStatusResponse.from(project);
    }

    @Override
    @Transactional(readOnly = true)
    public TeamManagementResponse getTeamManagement(Long projectId, String requesterEmail) {
        // 1단계: 엔티티 조회
        Project project = projectRepository.findByIdWithMembers(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        // 2단계: 권한 검증
        project.validateLeaderPermission(requesterEmail);

        // 3단계: 응답 생성
        return TeamManagementResponse.builder()
                .currentMemberCount(project.getMemberCount())
                .totalRecruitmentCount(project.getTotalRecruitmentCount())
                .pendingApplicationCount(project.getPendingApplicationCount())
                .members(toTeamMemberInfoList(project.getMembers()))
                .build();
    }

    private List<TeamManagementResponse.TeamMemberInfo> toTeamMemberInfoList(List<ProjectMember> members) {
        return members.stream()
                .map(this::toTeamMemberInfo)
                .toList();
    }

    private TeamManagementResponse.TeamMemberInfo toTeamMemberInfo(ProjectMember pm) {
        return TeamManagementResponse.TeamMemberInfo.builder()
                .memberId(pm.getMember().getId())
                .name(pm.getMember().getRealName())
                .profileImageUrl(pm.getMember().getStoreFileName())
                .jobFieldName(pm.getJobPosition().getJobField().getName())
                .jobPositionName(pm.getJobPosition().getName())
                .isLeader(pm.getRole() == ProjectMemberRole.LEADER)
                .build();
    }

    @Override
    public MemberExpelResponse expelMember(Long projectId, Long memberId, String requesterEmail) {
        // 1단계: 엔티티 조회 및 기본 검증 (모집 정보 포함)
        Project project = projectRepository.findByIdWithRecruitment(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.isDeleted()) {
            throw new CustomException(ErrorCode.PROJECT_NOT_FOUND);
        }

        project.validateNotCompleted();

        // 2단계: 권한 검증 (리더만 방출 가능)
        project.validateLeaderPermission(requesterEmail);

        // 3단계: 비즈니스 규칙 검증 (리더 본인은 방출 불가)
        if (project.isLeader(memberId)) {
            throw new CustomException(ErrorCode.CREATOR_DELETE_FORBIDDEN);
        }

        // 방출 대상 멤버 확인
        ProjectMember projectMember = projectMemberRepository.findByProjectIdAndMemberId(projectId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        String expelledMemberName = projectMember.getMember().getRealName();
        Long jobPositionId = projectMember.getJobPosition().getId();

        // 4단계: 멤버 삭제
        projectMemberRepository.delete(projectMember);

        // 5단계: 해당 포지션의 모집 인원 감소
        project.getRecruitments().stream()
                .filter(r -> r.getJobPosition().getId().equals(jobPositionId))
                .findFirst()
                .ifPresent(recruitmentState -> {
                    recruitmentState.decreaseCurrentCount();
                    project.updateRecruitmentsStatus();
                    log.info("모집 인원 감소 - projectId: {}, jobPositionId: {}, newCurrentCount: {}",
                            projectId, jobPositionId, recruitmentState.getCurrentCount());
                });

        log.info("팀원 방출 완료 - projectId: {}, expelledMemberId: {}, requesterEmail: {}",
                projectId, memberId, requesterEmail);

        // 6단계: 응답 반환
        return MemberExpelResponse.of(projectId, memberId, expelledMemberName);
    }
}