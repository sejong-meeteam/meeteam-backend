package com.wardk.meeteam_backend.domain.projectlike.service;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectlike.entity.ProjectLike;
import com.wardk.meeteam_backend.domain.projectlike.repository.ProjectLikeRepository;
import com.wardk.meeteam_backend.global.aop.Retry;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.projectlike.dto.response.LikeStatusResponse;
import com.wardk.meeteam_backend.web.projectlike.dto.response.ToggleLikeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectLikeService {

    private final ProjectRepository projectRepository;
    private final ProjectLikeRepository projectLikeRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public LikeStatusResponse status(Long memberId, Long projectId) {

        boolean status = projectLikeRepository.existsByMemberIdAndProjectId(memberId, projectId);

        return new LikeStatusResponse(status);
    }


    @Transactional
    public ToggleLikeResponse toggleWithPessimistic(Long projectId, String email) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        Member member = memberRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 1) Optional 로 좋아요 여부 확인
        Optional<ProjectLike> existing = projectLikeRepository.findByMemberAndProject(member, project);

        if (existing.isPresent()) {
            // 이미 눌렀으면 삭제
            projectLikeRepository.delete(existing.get());
            project.decreaseLike();
            return new ToggleLikeResponse(projectId, false, project.getLikeCount());
        }

        // 2) 없으면 삽입
        projectLikeRepository.save(ProjectLike.create(member, project));
        project.increaseLike();

        return new ToggleLikeResponse(projectId, true, project.getLikeCount());
    }


    @Retry
    @Transactional
    public ToggleLikeResponse toggleWithOptimistic(Long projectId, String email) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        Member member = memberRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 1) Optional 로 좋아요 여부 확인
        Optional<ProjectLike> existing = projectLikeRepository.findByMemberAndProject(member, project);

        if (existing.isPresent()) {
            // 이미 눌렀으면 삭제
            projectLikeRepository.delete(existing.get());
            project.decreaseLike();
            return new ToggleLikeResponse(projectId, false, project.getLikeCount());
        }

        // 2) 없으면 삽입
        projectLikeRepository.save(ProjectLike.create(member, project));
        project.increaseLike();

        return new ToggleLikeResponse(projectId, true, project.getLikeCount());
    }


}
