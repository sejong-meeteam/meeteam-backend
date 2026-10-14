package com.wardk.meeteam_backend.domain.application.service;

import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.application.dto.request.*;
import com.wardk.meeteam_backend.web.application.dto.response.*;

import java.util.List;

/**
 * 프로젝트 지원 서비스 인터페이스.
 */
public interface ProjectApplicationService {

    /**
     * 프로젝트 지원.
     */
    ApplicationResponse apply(Long projectId, Long memberId, ApplicationRequest request);

    /**
     * 프로젝트 지원자 목록 조회 (리더 전용).
     */
    List<ProjectApplicationListResponse> getApplicationList(Long projectId, String requesterEmail);

    /**
     * 프로젝트 지원 상세 조회 (리더 전용).
     */
    ApplicationDetailResponse getApplicationDetail(Long projectId, Long applicationId, String requesterEmail);

    /**
     * 지원자 승인/거절 (리더 전용).
     */
    ApplicationDecisionResponse decide(Long applicationId, ApplicationDecisionRequest request, String requesterEmail);

    /**
     * 내가 지원한 프로젝트 목록 조회.
     */
    List<AppliedProjectResponse> getAppliedProjects(CustomSecurityUserDetails userDetails);

    /**
     * 프로젝트 지원 페이지 정보 조회.
     * 프로젝트 리더는 접근 불가.
     */
    ApplicationPageResponse getApplicationPage(Long projectId, Long memberId);

    /**
     * 지원 취소.
     * 본인만 취소 가능하며, PENDING 상태에서만 취소 가능.
     */
    ApplicationCancelResponse cancel(Long applicationId, Long memberId);
}
