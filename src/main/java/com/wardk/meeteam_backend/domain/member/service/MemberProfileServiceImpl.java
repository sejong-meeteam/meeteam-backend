package com.wardk.meeteam_backend.domain.member.service;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.entity.TechStack;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.job.repository.TechStackRepository;
import com.wardk.meeteam_backend.domain.file.service.S3FileService;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.projectlike.repository.ProjectLikeRepository;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.recruitment.repository.RecruitmentStateRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.mainpage.dto.response.MemberCardResponse;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import com.wardk.meeteam_backend.web.auth.dto.register.TechStackOrderRequest;
import com.wardk.meeteam_backend.web.member.dto.request.MemberProfileUpdateRequest;
import com.wardk.meeteam_backend.web.member.dto.response.MemberDetailResponse;
import com.wardk.meeteam_backend.web.member.dto.response.MemberProfileResponse;
import com.wardk.meeteam_backend.web.member.dto.response.MemberProfileUpdateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberProfileServiceImpl implements MemberProfileService {

    private final MemberRepository memberRepository;
    private final JobPositionRepository jobPositionRepository;
    private final TechStackRepository techStackRepository;
    private final S3FileService s3FileService;
    private final RecruitmentStateRepository recruitmentStateRepository;
    private final ProjectLikeRepository projectLikeRepository;

    /**
     * 나의 프로필 조회 (프로필 사진 분기처리 포함)
     */
    @Override
    @Transactional(readOnly = true)
    public MemberProfileResponse profile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        MemberProfileResponse memberProfileResponse = new MemberProfileResponse(member, memberId);

        // 프로필 사진 처리
        if (member.getStoreFileName() != null && !member.getStoreFileName().trim().isEmpty()) {
            memberProfileResponse.setProfileImageUrl(member.getStoreFileName());
            memberProfileResponse.setProfileImageName("profile.jpg");
            log.debug("프로필 사진 있음: {}", member.getStoreFileName());
        } else {
            memberProfileResponse.setProfileImageUrl(null);
            memberProfileResponse.setProfileImageName(null);
            log.debug("프로필 사진 없음 - 회원 ID: {}", memberId);
        }

        // 참여 프로젝트 -> ProjectCardResponse 변환 (배치 쿼리)
        List<Project> projects = member.getProjectMembers().stream()
            .map(pm -> pm.getProject())
            .filter(p -> !p.isDeleted())
            .toList();

        if (!projects.isEmpty()) {
            List<Long> projectIds = projects.stream().map(Project::getId).toList();

            Map<Long, List<RecruitmentState>> recruitmentMap = recruitmentStateRepository
                .findAllByProjectIdsWithDetails(projectIds).stream()
                .collect(Collectors.groupingBy(rs -> rs.getProject().getId()));

            Set<Long> likedIds = projectLikeRepository.findLikedProjectIds(memberId, projectIds);

            List<ProjectCardResponse> cards = projects.stream()
                .map(project -> {
                    List<RecruitmentState> recs = recruitmentMap.getOrDefault(project.getId(),
                        Collections.emptyList());
                    return ProjectCardResponse.from(project, recs, likedIds.contains(project.getId()));
                }).toList();

            memberProfileResponse.setProjectCards(cards);
        } else {
            memberProfileResponse.setProjectCards(List.of());
        }

        return memberProfileResponse;
    }

    /**
     * 특정 사용자 상세 조회 (v1).
     */
    @Override
    @Transactional(readOnly = true)
    public MemberDetailResponse getMemberDetail(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 참여 프로젝트 -> ProjectCardResponse 변환 (배치 쿼리)
        List<Project> projects = member.getProjectMembers().stream()
                .map(pm -> pm.getProject())
                .filter(p -> !p.isDeleted())
                .toList();

        List<ProjectCardResponse> projectCards;
        if (!projects.isEmpty()) {
            List<Long> projectIds = projects.stream().map(Project::getId).toList();

            Map<Long, List<RecruitmentState>> recruitmentMap = recruitmentStateRepository
                    .findAllByProjectIdsWithDetails(projectIds).stream()
                    .collect(Collectors.groupingBy(rs -> rs.getProject().getId()));

            // 비로그인 사용자는 좋아요 여부 항상 false
            projectCards = projects.stream()
                    .map(project -> {
                        List<RecruitmentState> recs = recruitmentMap.getOrDefault(project.getId(),
                                Collections.emptyList());
                        return ProjectCardResponse.from(project, recs, false);
                    }).toList();
        } else {
            projectCards = List.of();
        }

        return MemberDetailResponse.from(member, projectCards);
    }

    /**
     * 회원 프로필 수정 (프로필 이미지 포함)
     */
    @Override
    @Transactional
    public MemberProfileUpdateResponse updateProfile(Long memberId, MemberProfileUpdateRequest request, MultipartFile profileImage) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 프로필 사진 처리 (새 사진이 업로드된 경우에만)
        if (profileImage != null && !profileImage.isEmpty()) {
            log.info("새 프로필 사진 업로드 요청 - 파일명: {}, 크기: {}",
                    profileImage.getOriginalFilename(), profileImage.getSize());

            // 기존 프로필 사진 삭제 (있다면)
            if (member.getStoreFileName() != null && !member.getStoreFileName().trim().isEmpty()) {
                try {
                    s3FileService.deleteFile(member.getStoreFileName());
                    log.info("기존 프로필 사진 삭제 완료: {}", member.getStoreFileName());
                } catch (Exception e) {
                    log.warn("기존 프로필 사진 삭제 실패: {}", e.getMessage());
                }
            }

            // 새 프로필 사진 S3 업로드
            String profileImageUrl = s3FileService.uploadFile(profileImage, "profiles", memberId);
            member.setStoreFileName(profileImageUrl);
            log.info("새 프로필 사진 업로드 완료: {}", profileImageUrl);
        } else {
            log.debug("프로필 사진 변경 없음 - 기존 사진 유지");
        }

        // 기본 정보 수정
        member.updateProfile(
            request.getName(),
            request.getAge(),
            request.getGender(),
            request.getIsParticipating(),
            request.getIntroduction(),
            request.getGithubUrl(),
            request.getBlogUrl()
        );

        // 기존 관심 분야 삭제 후 새로 추가
        updateMemberJobPositions(member, request.getJobPositionIds());

        // 기존 기술 스택 삭제 후 새로 추가
        updateMemberTechStacks(member, request.getTechStacks());

        // 변경 사항 저장 (더티 체킹으로 자동 업데이트)
        Member savedMember = memberRepository.save(member);

        return new MemberProfileUpdateResponse(savedMember);
    }


    /**
     * 메인페이지 유저 카드 목록 조회.
     * projectCount 정렬 지원 (sort=projectCount,desc)
     */
    @Override
    @Transactional(readOnly = true)
    public Page<MemberCardResponse> getMainPageMembers(Pageable pageable) {
        Page<Member> memberPage = memberRepository.findAllWithProjectCountSort(pageable);

        return memberPage.map(MemberCardResponse::from);
    }

    /**
     * 팀원 찾기 v1 검색.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<MemberCardResponse> searchMembersV1(String name, Long jobFieldId,
                                                     List<String> techStackNames, Pageable pageable) {
        Page<Member> memberPage = memberRepository.searchMembersV1(name, jobFieldId, techStackNames, pageable);

        log.info("팀원 검색 v1 - name: {}, jobFieldId: {}, techStacks: {}, 총 {}명",
                name, jobFieldId, techStackNames, memberPage.getTotalElements());

        return memberPage.map(MemberCardResponse::from);
    }

    /**
     * 회원 관심 분야 업데이트
     */
    private void updateMemberJobPositions(Member member, List<Long> jobPositionIds) {
        member.getJobPositions().clear();

        if (jobPositionIds == null || jobPositionIds.isEmpty()) {
            return;
        }

        List<JobPosition> jobPositions = jobPositionRepository.findAllById(jobPositionIds);
        if (jobPositions.size() != jobPositionIds.size()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        for (JobPosition jobPosition : jobPositions) {
            member.addJobPosition(jobPosition);
        }
    }

    /**
     * 회원 기술 스택 업데이트 (displayOrder 지원)
     */
    private void updateMemberTechStacks(Member member, List<TechStackOrderRequest> techStackRequests) {
        member.getMemberTechStacks().clear();

        if (techStackRequests == null || techStackRequests.isEmpty()) {
            return;
        }

        List<Long> techStackIds = techStackRequests.stream()
                .map(TechStackOrderRequest::id)
                .toList();

        List<TechStack> techStacks = techStackRepository.findAllById(techStackIds);
        if (techStacks.size() != techStackIds.size()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // ID로 TechStack 매핑
        Map<Long, TechStack> techStackMap = techStacks.stream()
                .collect(Collectors.toMap(TechStack::getId, ts -> ts));

        // 요청의 displayOrder 사용
        for (TechStackOrderRequest request : techStackRequests) {
            TechStack techStack = techStackMap.get(request.id());
            if (techStack != null) {
                member.addMemberTechStack(techStack, request.displayOrder());
            }
        }
    }


}
