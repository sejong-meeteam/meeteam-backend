package com.wardk.meeteam_backend.web.notification.context;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.notification.NotificationEvent;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 알림 Payload 생성에 필요한 컨텍스트 정보를 담는 클래스.
 * 엔티티 참조 대신 필요한 데이터만 포함하여 레이어 간 의존성을 낮춤.
 */
@Getter
@Builder
public class NotificationContext {

    // 기본 ID 정보
    private final Long receiverId;
    private final Long projectId;
    private final Long actorId;
    private final Long applicationId;

    // Payload 생성에 필요한 추가 데이터
    private final String projectName;
    private final String actorName;

    private final LocalDate occurredAt;

    /**
     * NotificationEvent와 조회한 엔티티로부터 컨텍스트 생성
     */
    public static NotificationContext of(NotificationEvent event, Project project, Member actor) {
        return NotificationContext.builder()
                .receiverId(event.getReceiverId())
                .projectId(event.getProjectId())
                .actorId(event.getActorId())
                .applicationId(event.getApplicationId())
                .projectName(project != null ? project.getName() : null)
                .actorName(actor != null ? actor.getRealName() : null)
                .occurredAt(LocalDate.now())
                .build();
    }

    /**
     * PROJECT_ENDED 이벤트용 컨텍스트 생성
     */
    public static NotificationContext forProjectEnded(Long receiverId, Long projectId, String projectName) {
        return NotificationContext.builder()
                .receiverId(receiverId)
                .projectId(projectId)
                .projectName(projectName)
                .occurredAt(LocalDate.now())
                .build();
    }
}
