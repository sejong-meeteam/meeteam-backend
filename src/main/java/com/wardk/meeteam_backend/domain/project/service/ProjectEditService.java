package com.wardk.meeteam_backend.domain.project.service;

import com.wardk.meeteam_backend.domain.project.service.dto.ProjectEditCommand;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectEditPrefillResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectEditResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로젝트 수정 관련 비즈니스 로직을 정의하는 서비스 인터페이스.
 */
public interface ProjectEditService {

    /**
     * 프로젝트 수정 페이지용 Pre-fill 정보를 조회합니다.
     * 리더만 조회 가능합니다.
     *
     * @param projectId 프로젝트 ID
     * @param requesterEmail 요청자 이메일
     * @return 수정 페이지 Pre-fill 정보
     */
    ProjectEditPrefillResponse getEditPrefill(Long projectId, String requesterEmail);

    /**
     * 프로젝트를 수정합니다.
     * 리더만 수정 가능하며, 모집 중단 상태에서는 수정 불가합니다.
     *
     * @param projectId 프로젝트 ID
     * @param command 수정 커맨드
     * @param file 프로젝트 이미지 파일
     * @param requesterEmail 요청자 이메일
     * @return 수정 결과
     */
    ProjectEditResponse update(Long projectId, ProjectEditCommand command, MultipartFile file, String requesterEmail);
}