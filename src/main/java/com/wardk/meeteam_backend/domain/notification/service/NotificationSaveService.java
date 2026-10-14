package com.wardk.meeteam_backend.domain.notification.service;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.notification.entity.Notification;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.domain.notification.repository.NotificationRepository;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 저장 전용 서비스.
 *
 * 모든 알림 저장 로직을 이 클래스에서 통합 관리합니다.
 * 비즈니스 서비스에서 이 서비스를 통해 알림을 저장하고,
 * SSENotificationService는 전송만 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationSaveService {

    private final NotificationRepository notificationRepository;

    /**
     * 단일 알림 저장
     *
     * @param receiver      알림 수신자
     * @param project       관련 프로젝트
     * @param actorId       행위자 ID (지원자 등)
     * @param type          알림 타입
     * @param applicationId 지원서 ID (nullable)
     * @return 저장된 알림
     */
    public Notification save(Member receiver, Project project, Long actorId,
                             NotificationType type, Long applicationId) {
        Notification notification = Notification.builder()
                .receiver(receiver)
                .project(project)
                .actorId(actorId)
                .type(type)
                .applicationId(applicationId)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.debug("[알림 저장] type: {}, receiver: {}, project: {}",
                type, receiver.getId(), project.getId());

        return saved;
    }

    /**
     * 프로젝트 종료 알림 일괄 저장
     *
     * @param project 종료되는 프로젝트
     * @param members 알림 받을 팀원 목록
     * @return 저장된 알림 목록
     */
    public List<Notification> saveForProjectEnd(Project project, List<Member> members) {
        List<Notification> notifications = members.stream()
                .map(member -> Notification.builder()
                        .receiver(member)
                        .project(project)
                        .actorId(null)
                        .type(NotificationType.PROJECT_END)
                        .applicationId(null)
                        .isRead(false)
                        .build())
                .toList();

        List<Notification> savedNotifications = notificationRepository.saveAll(notifications);
        log.info("[알림 저장] PROJECT_END 알림 {}건 저장 - projectId: {}",
                savedNotifications.size(), project.getId());

        return savedNotifications;
    }

    /**
     * 지원 알림 저장 (팀장 + 지원자)
     *
     * @param projectCreator 프로젝트 생성자 (팀장)
     * @param applicant      지원자
     * @param project        지원한 프로젝트
     * @param applicationId  지원서 ID
     */
    public void saveForApply(Member projectCreator, Member applicant,
                             Project project, Long applicationId) {
        // 팀장에게 지원 알림
        save(projectCreator, project, applicant.getId(),
                NotificationType.PROJECT_APPLY, applicationId);

        // 지원자에게 지원 완료 알림
        save(applicant, project, applicant.getId(),
                NotificationType.PROJECT_MY_APPLY, applicationId);
    }

    /**
     * 지원 승인 알림 저장
     *
     * @param applicant       지원자
     * @param project         프로젝트
     * @param projectCreatorId 프로젝트 생성자 ID
     * @param applicationId   지원서 ID
     */
    public void saveForApprove(Member applicant, Project project,
                               Long projectCreatorId, Long applicationId) {
        save(applicant, project, projectCreatorId,
                NotificationType.PROJECT_APPROVE, applicationId);
    }

    /**
     * 지원 거절 알림 저장
     *
     * @param applicant       지원자
     * @param project         프로젝트
     * @param projectCreatorId 프로젝트 생성자 ID
     * @param applicationId   지원서 ID
     */
    public void saveForReject(Member applicant, Project project,
                              Long projectCreatorId, Long applicationId) {
        save(applicant, project, projectCreatorId,
                NotificationType.PROJECT_REJECT, applicationId);
    }
}