package com.wardk.meeteam_backend.domain.project.service;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectlike.repository.ProjectLikeRepository;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.recruitment.repository.RecruitmentStateRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.mainpage.dto.request.CategoryCondition;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectSearchCondition;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectSearchRequest;
import com.wardk.meeteam_backend.web.project.dto.response.MyProjectResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectDetailResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 프로젝트 조회/검색 서비스 구현체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryServiceImpl implements ProjectQueryService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectLikeRepository projectLikeRepository;
    private final RecruitmentStateRepository recruitmentStateRepository;


    @Override
    public List<ProjectListResponse> findAll() {
        List<Project> projects = projectRepository.findAllWithCreatorAndSkills();
        return projects.stream()
                .map(ProjectListResponse::responseDto)
                .toList();
    }



    @Override
    public ProjectDetailResponse findById(Long projectId, Long memberId) {
        Project project = projectRepository.findProjectDetailById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        // 좋아요 여부 확인 (비로그인 시 false)
        boolean isLiked = memberId != null
                && projectLikeRepository.existsByMemberIdAndProjectId(memberId, projectId);

        // 리더 여부 확인 (비로그인 시 false)
        boolean isLeader = memberId != null && project.isLeader(memberId);

        return ProjectDetailResponse.from(project, isLiked, isLeader);
    }



    @Override
    public List<MyProjectResponse> findMyProjects(CustomSecurityUserDetails userDetails) {
        List<ProjectMember> projectMembers = projectMemberRepository.findAllByMemberId(userDetails.getMemberId());
        return projectMembers.stream()
                .map(MyProjectResponse::responseDto)
                .toList();
    }



    @Override
    public Page<ProjectCardResponse> search(ProjectSearchCondition condition, Pageable pageable,
            CustomSecurityUserDetails userDetails) {
        Page<Project> projects = projectRepository.findAllSlicedForSearchAtCondition(condition, pageable);
        return toProjectCardPage(projects, userDetails);
    }



    @Cacheable(
            value = "mainPageProjects",
            key = "'page_0_size_20_sort_' + #pageable.sort.toString() + '_category_all'",
            condition = "#pageable.pageNumber == 0 && #userDetails == null && #pageable.pageSize == 20 && #condition.projectCategory == null"
    )
    @Override
    public Page<ProjectCardResponse> searchForMainPage(CategoryCondition condition, Pageable pageable,
            CustomSecurityUserDetails userDetails) {
        Page<Project> projects = projectRepository.findProjectsFromMainPageCondition(condition, pageable);
        return toProjectCardPage(projects, userDetails);
    }



    private Page<ProjectCardResponse> toProjectCardPage(Page<Project> projects, CustomSecurityUserDetails userDetails) {
        List<Long> projectIds = projects.getContent().stream()
                .map(Project::getId)
                .toList();

        if (projectIds.isEmpty()) {
            return projects.map(p -> null);
        }

        // 배치 쿼리 1: 모집 현황 + 기술스택 한 번에 조회
        List<RecruitmentState> allRecruitments = recruitmentStateRepository.findAllByProjectIdsWithDetails(projectIds);
        Map<Long, List<RecruitmentState>> recruitmentMap = allRecruitments.stream()
                .collect(Collectors.groupingBy(rs -> rs.getProject().getId()));

        // 배치 쿼리 2: 좋아요 여부 한 번에 조회
        Set<Long> likedIds = findLikedProjectIds(userDetails, projectIds);

        return projects.map(project -> {
            List<RecruitmentState> recs = recruitmentMap.getOrDefault(project.getId(), Collections.emptyList());
            return ProjectCardResponse.from(project, recs, likedIds.contains(project.getId()));
        });
    }



    private Set<Long> findLikedProjectIds(CustomSecurityUserDetails userDetails, List<Long> projectIds) {
        if (userDetails == null) {
            return Collections.emptySet();
        }
        return projectLikeRepository.findLikedProjectIds(userDetails.getMemberId(), projectIds);
    }



    @Override
    public Slice<ProjectCardResponse> searchV1(ProjectSearchRequest request, Pageable pageable,
            CustomSecurityUserDetails userDetails) {
        // 정렬 적용
        Sort sort = request.sort().toSort();
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort
        );

        // 기존 조건 객체로 변환 후 검색 (Slice 사용 - count 쿼리 없음)
        ProjectSearchCondition condition = request.toCondition();
        Slice<Project> projects = projectRepository.searchWithSlice(condition, sortedPageable);

        return toProjectCardSlice(projects, userDetails);
    }

    private Slice<ProjectCardResponse> toProjectCardSlice(Slice<Project> projects, CustomSecurityUserDetails userDetails) {
        List<Long> projectIds = projects.getContent().stream()
                .map(Project::getId)
                .toList();

        if (projectIds.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), projects.getPageable(), projects.hasNext());
        }

        // 배치 쿼리 1: 모집 현황 + 기술스택 한 번에 조회
        List<RecruitmentState> allRecruitments = recruitmentStateRepository.findAllByProjectIdsWithDetails(projectIds);
        Map<Long, List<RecruitmentState>> recruitmentMap = allRecruitments.stream()
                .collect(Collectors.groupingBy(rs -> rs.getProject().getId()));

        // 배치 쿼리 2: 좋아요 여부 한 번에 조회
        Set<Long> likedIds = findLikedProjectIds(userDetails, projectIds);

        List<ProjectCardResponse> content = projects.getContent().stream()
                .map(project -> {
                    List<RecruitmentState> recs = recruitmentMap.getOrDefault(project.getId(), Collections.emptyList());
                    return ProjectCardResponse.from(project, recs, likedIds.contains(project.getId()));
                })
                .toList();

        return new SliceImpl<>(content, projects.getPageable(), projects.hasNext());
    }
}