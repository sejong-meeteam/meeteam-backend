package com.wardk.meeteam_backend.domain.project.service;

import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.mainpage.dto.request.CategoryCondition;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectSearchCondition;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectSearchRequest;
import com.wardk.meeteam_backend.web.project.dto.response.MyProjectResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectDetailResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * 프로젝트 조회/검색 관련 비즈니스 로직을 정의하는 서비스 인터페이스.
 */
public interface ProjectQueryService {

    /**
     * 모든 프로젝트 목록을 조회합니다.
     *
     * @return 프로젝트 목록
     */
    List<ProjectListResponse> findAll();

    /**
     * 프로젝트 상세 정보를 조회합니다.
     *
     * @param projectId 프로젝트 ID
     * @param memberId 현재 로그인한 사용자 ID (비로그인 시 null)
     * @return 프로젝트 상세 정보
     */
    ProjectDetailResponse findById(Long projectId, Long memberId);

    /**
     * 사용자가 참여 중인/완료한 프로젝트 목록을 조회합니다.
     *
     * @param userDetails 현재 로그인한 사용자 정보
     * @return 내 프로젝트 목록
     */
    List<MyProjectResponse> findMyProjects(CustomSecurityUserDetails userDetails);

    /**
     * 조건에 맞는 프로젝트를 검색합니다.
     *
     * @param condition 검색 조건
     * @param pageable 페이징 정보
     * @param userDetails 현재 로그인한 사용자 정보
     * @return 검색된 프로젝트 목록 (페이징)
     */
    Page<ProjectCardResponse> search(ProjectSearchCondition condition, Pageable pageable, CustomSecurityUserDetails userDetails);

    /**
     * 메인 페이지용 프로젝트 목록을 조회합니다.
     *
     * @param condition 카테고리 조건
     * @param pageable 페이징 정보
     * @param userDetails 현재 로그인한 사용자 정보
     * @return 프로젝트 카드 목록 (페이징)
     */
    Page<ProjectCardResponse> searchForMainPage(CategoryCondition condition, Pageable pageable, CustomSecurityUserDetails userDetails);

    /**
     * V1 API: 조건에 맞는 프로젝트를 검색합니다 (무한스크롤용, count 쿼리 없음).
     *
     * @param request 검색 요청 (키워드, 카테고리, 모집상태, 플랫폼, 직군, 기술스택, 정렬)
     * @param pageable 페이징 정보
     * @param userDetails 현재 로그인한 사용자 정보 (비로그인 시 null)
     * @return 검색된 프로젝트 목록 (Slice - 다음 페이지 존재 여부만 포함)
     */
    Slice<ProjectCardResponse> searchV1(ProjectSearchRequest request, Pageable pageable, CustomSecurityUserDetails userDetails);
}