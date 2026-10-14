package com.wardk.meeteam_backend.domain.notification;


import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;



@Data
// 알림에 필요한 최소 정보만 담는 POJO
public class NotificationEvent {

    private Long receiverId;          // 알림 받을 사람
    private Long projectId;           // 관련 프로젝트
    private Long actorId;            // 행위자(지원자) - 없으면 null
    private NotificationType type;      // 예: PROJECT_APPLY
    private Long applicationId;
    // 메시지


    public NotificationEvent(Long receiverId, Long projectId, Long actorId, NotificationType type, Long applicationId) {
        this.receiverId = receiverId;
        this.projectId = projectId;
        this.actorId = actorId;
        this.type = type;
        this.applicationId = applicationId;
    }


    public NotificationEvent(Long receiverId, Long projectId, Long actorId, NotificationType type) {
        this.receiverId = receiverId;
        this.projectId = projectId;
        this.actorId = actorId;
        this.type = type;
    }
}
