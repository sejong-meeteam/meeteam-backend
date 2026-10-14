package com.wardk.meeteam_backend.domain.project.service;

import com.wardk.meeteam_backend.web.project.dto.response.MemberExpelResponse;
import com.wardk.meeteam_backend.web.project.dto.response.RecruitmentStatusResponse;
import com.wardk.meeteam_backend.web.project.dto.response.TeamManagementResponse;

/**
 * 프로젝트 관리(모집상태, 팀원관리) 관련 비즈니스 로직을 정의하는 서비스 인터페이스.
 */
public interface ProjectManagementService {

    /**
     * 프로젝트 모집 상태를 토글합니다.
     * 모집중 ↔ 모집중단
     *
     * @param projectId 프로젝트 ID
     * @param requesterEmail 요청자 이메일
     * @return 변경된 모집 상태 정보
     */
    RecruitmentStatusResponse toggleRecruitmentStatus(Long projectId, String requesterEmail);

    /**
     * 프로젝트 팀원 관리 정보를 조회합니다.
     * 리더만 조회 가능합니다.
     *
     * @param projectId 프로젝트 ID
     * @param requesterEmail 요청자 이메일
     * @return 팀원 관리 정보
     */
    TeamManagementResponse getTeamManagement(Long projectId, String requesterEmail);

    /**
     * 프로젝트 팀원을 방출합니다.
     * 리더만 방출 가능하며, 리더 본인은 방출할 수 없습니다.
     *
     * @param projectId 프로젝트 ID
     * @param memberId 방출할 멤버 ID
     * @param requesterEmail 요청자 이메일
     * @return 방출 결과
     */
    MemberExpelResponse expelMember(Long projectId, Long memberId, String requesterEmail);
}