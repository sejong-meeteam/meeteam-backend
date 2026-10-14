package com.wardk.meeteam_backend.domain.notification.entity;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.notification.NotificationEvent;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.global.entity.BaseEntity;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 알림 받을 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Member receiver;

    private Long actorId;// 보낸사람ID (ex : 프로젝트 지원한 memberId)

    @Nullable
    private Long applicationId; // 지원서ID

    // 알림 타입 (지원, 승인, 거절, 댓글 등)
    @Enumerated(EnumType.STRING)
    private NotificationType type;


    // 읽음 여부
    private boolean isRead;

    public void readNotification() {
        this.isRead = true;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Builder
    private Notification(Member receiver, NotificationType type,boolean isRead, Project project, Long actorId, Long applicationId) {
        this.receiver = receiver;
        this.type = type;
        this.isRead = isRead;
        this.project = project;
        this.actorId = actorId;
        this.applicationId = applicationId;
    }


}