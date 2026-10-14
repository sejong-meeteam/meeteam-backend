package com.wardk.meeteam_backend.domain.project.service;

import com.wardk.meeteam_backend.domain.project.service.dto.ProjectPostCommand;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectDeleteResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectPostResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로젝트 생성/삭제 관련 비즈니스 로직을 정의하는 서비스 인터페이스.
 */
public interface ProjectCommandService {

    /**
     * 새 프로젝트를 생성합니다.
     *
     * @param command 프로젝트 생성 커맨드
     * @param file 프로젝트 이미지 파일
     * @param requesterEmail 요청자 이메일
     * @return 생성된 프로젝트 정보
     */
    ProjectPostResponse create(ProjectPostCommand command, MultipartFile file, String requesterEmail);

    /**
     * 프로젝트를 삭제합니다.
     * 리더만 삭제 가능합니다.
     *
     * @param projectId 프로젝트 ID
     * @param requesterEmail 요청자 이메일
     * @return 삭제된 프로젝트 정보
     */
    ProjectDeleteResponse delete(Long projectId, String requesterEmail);
}