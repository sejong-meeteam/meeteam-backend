package com.wardk.meeteam_backend.domain.projectmember.service;

import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMemberRole;
import com.wardk.meeteam_backend.global.aop.Retry;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.web.projectmember.dto.request.*;
import com.wardk.meeteam_backend.web.projectmember.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final MemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public void addCreator(Long projectId, Long memberId, JobPosition jobPosition) {
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
            throw new CustomException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }

        ProjectMember projectMember = ProjectMember.createProjectMember(member, jobPosition, ProjectMemberRole.LEADER);
        project.joinMember(projectMember);
        projectMemberRepository.save(projectMember);
    }

    @Retry
    @Override
    public void addMember(Long projectId, Long memberId, JobPosition jobPosition) {

        Project project = projectRepository.findByIdWithRecruitment(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
            throw new CustomException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }

        ProjectMember projectMember = ProjectMember.createProjectMember(member, jobPosition, ProjectMemberRole.MEMBER);
        project.joinMember(projectMember);

        RecruitmentState recruitmentState = project.getRecruitments().stream()
                .filter(r -> r.getJobPosition().getId().equals(jobPosition.getId()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        recruitmentState.increaseCurrentCount();
        project.updateRecruitmentsStatus();
        projectMemberRepository.save(projectMember);
    }

    @Override
    public List<ProjectMemberListResponse> getProjectMembers(Long projectId) {
        Project project = projectRepository.findByIdWithMembers(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        return project.getMembers().stream()
                .map(pm -> {
                    Member member = pm.getMember();
                    return ProjectMemberListResponse.responseDto(
                            member.getId(),
                            member.getRealName(),
                            member.getStoreFileName(),
                            pm.getRole() == ProjectMemberRole.LEADER
                    );
                }).toList();
    }

    @Override
    public DeleteResponse deleteProjectMember(DeleteRequest request, String requesterEmail) {
        Project project = projectRepository.findActiveById(request.getProjectId())
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.isCompleted()) {
            throw new CustomException(ErrorCode.PROJECT_ALREADY_COMPLETED);
        }

        Member creator = project.getCreator();

        if (!creator.getEmail().equals(requesterEmail)) {
            throw new CustomException(ErrorCode.PROJECT_MEMBER_FORBIDDEN);
        }

        if (creator.getId().equals(request.getMemberId())) {
            throw new CustomException(ErrorCode.CREATOR_DELETE_FORBIDDEN);
        }

        if (!projectMemberRepository.existsByProjectIdAndMemberId(request.getProjectId(), request.getMemberId())) {
            throw new CustomException(ErrorCode.PROJECT_MEMBER_NOT_FOUND);
        }

        projectMemberRepository.deleteByProjectIdAndMemberId(request.getProjectId(), request.getMemberId());

        return DeleteResponse.responseDto(
                project.getId(),
                request.getMemberId()
        );
    }

    @Override
    public WithdrawResponse withdraw(WithdrawRequest request, String requesterEmail) {

        Project project = projectRepository.findByIdWithRecruitment(request.getProjectId())
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.isDeleted()) {
            throw new CustomException(ErrorCode.PROJECT_NOT_FOUND);
        }

        if (project.isCompleted()) {
            throw new CustomException(ErrorCode.PROJECT_ALREADY_COMPLETED);
        }

        // 소프트 삭제(deletedAt) 회원이 같은 이메일로 남아있을 수 있어 활성 회원만 조회 (NonUniqueResult 방지)
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(requesterEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (project.getCreator().getId().equals(member.getId())) {
            throw new CustomException(ErrorCode.CREATOR_DELETE_FORBIDDEN);
        }

        ProjectMember projectMember = projectMemberRepository.findByProjectIdAndMemberId(project.getId(), member.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        Long jobPositionId = projectMember.getJobPosition().getId();

        projectMemberRepository.delete(projectMember);

        // 해당 포지션의 모집 인원 감소
        project.getRecruitments().stream()
                .filter(r -> r.getJobPosition().getId().equals(jobPositionId))
                .findFirst()
                .ifPresent(recruitmentState -> {
                    recruitmentState.decreaseCurrentCount();
                    project.updateRecruitmentsStatus();
                });

        return WithdrawResponse.responseDto(
                project.getId(),
                member.getId()
        );
    }
}
