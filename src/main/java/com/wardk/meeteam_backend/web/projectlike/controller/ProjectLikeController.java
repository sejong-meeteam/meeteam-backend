package com.wardk.meeteam_backend.web.projectlike.controller;

import com.wardk.meeteam_backend.domain.projectlike.service.ProjectLikeService;
import com.wardk.meeteam_backend.global.aop.aspect.OptimisticLockRetryAspect;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.projectlike.dto.response.LikeStatusResponse;
import com.wardk.meeteam_backend.web.projectlike.dto.response.ToggleLikeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로젝트 좋아요(Like) 토글 API 컨트롤러.
 *
 * <h2>동시성 · 무결성 보장 — Lost Update 방지</h2>
 * <p>이 컨트롤러는 {@link com.wardk.meeteam_backend.domain.projectlike.service.ProjectLikeService#toggle(Long, String)}
 * 를 호출하며, 서비스 계층에서 <b>낙관적 락(@Version)</b>과 <b>Retry AOP(@Retry)</b>를 조합해
 * 좋아요 수 증감 연산에서 발생할 수 있는 <i>Lost Update</i>를 방지합니다.</p>
 *
 * <ul>
 *   <li><b>낙관적 락</b> — Project 엔티티의 버전 필드를 통해 동시 갱신 충돌을 감지합니다.
 *       충돌 시 JPA가 {@code OptimisticLockException}을 발생시킵니다.</li>
 *   <li><b>Retry AOP</b> — {@link com.wardk.meeteam_backend.global.aop.Retry} 가 부착된 메서드는
 *       {@link OptimisticLockRetryAspect} 에 의해
 *       짧은 대기 후 최대 N회 자동 재시도되어, 일시적 버전 충돌을 해소합니다.</li>
 *   <li><b>결과적 일관성</b> — 중복 클릭/경합 상황에서도 최종 좋아요 집계 수가 정확히 반영됩니다.</li>
 * </ul>
 *
 * <h3>토글 동작 흐름</h3>
 * <ol>
 *   <li>이미 좋아요가 있으면 (memberId, projectId)로 삭제 → {@code project.decreaseLike()}.</li>
 *   <li>없으면 삽입 → {@code project.increaseLike()}.
 *       동시 삽입 경합 시 DB 유니크 제약으로 {@code DataIntegrityViolationException} 발생 가능.</li>
 *   <li>증감 시 버전 충돌이 발생하면 Retry AOP가 캡처하여 재시도해 Lost Update를 제거합니다.</li>
 * </ol>
 *
 * <h3>엔드포인트</h3>
 * <pre>{@code
 * POST /api/project/{projectId}
 * Authorization: Bearer &lt;JWT&gt;
 * }</pre>
 * 응답은 현재 좋아요 상태와 집계 수를 포함합니다.
 *
 * @see com.wardk.meeteam_backend.domain.projectlike.service.ProjectLikeService
 * @see OptimisticLockRetryAspect
 * @see com.wardk.meeteam_backend.global.aop.Retry
 */
@Tag(name = "Project Like", description = "프로젝트 좋아요 API")
@RestController
@RequiredArgsConstructor
public class ProjectLikeController {

    private final ProjectLikeService projectLikeService;


    @Operation(summary = "좋아요 토글", description = "프로젝트 좋아요를 등록하거나 취소합니다.")
    @PostMapping("/api/v1/project/like/{projectId}")
    public SuccessResponse<ToggleLikeResponse> toggleLike(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        return SuccessResponse.onSuccess(projectLikeService.toggleWithPessimistic(projectId, userDetails.getUsername()));
    }


    @Operation(summary = "좋아요 상태 조회", description = "프로젝트에 대한 좋아요 여부를 조회합니다.")
    @GetMapping("/api/v1/project/like/{projectId}")
    public SuccessResponse<LikeStatusResponse> likeStatus(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {

        return SuccessResponse.onSuccess(projectLikeService.status(userDetails.getMemberId(), projectId));
    }
}
