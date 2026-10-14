package com.wardk.meeteam_backend.domain.notification.service;


import com.wardk.meeteam_backend.domain.notification.entity.Notification;
import com.wardk.meeteam_backend.domain.notification.repository.NotificationRepository;
import com.wardk.meeteam_backend.web.notification.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Slice<NotificationResponse> getNotifications(Long memberId, Pageable pageable) {

        Slice<NotificationResponse> response = notificationRepository.findByMemberId
                (memberId, pageable)
                .map(NotificationResponse::new);

        notificationRepository.bulkIsRead(memberId);

        return response;

    }


    @Transactional
    public int getUnreadCount(Long memberId) {

        return notificationRepository.findUnreadCount(memberId);
    }
}
