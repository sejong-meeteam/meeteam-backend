package com.wardk.meeteam_backend.web.notification.controller;

import com.wardk.meeteam_backend.domain.notification.service.NotificationService;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSubscribeService;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.wardk.meeteam_backend.web.notification.dto.response.NotificationResponse;
import com.wardk.meeteam_backend.web.notification.dto.response.NotificationUnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 알림을 위한 Server-Sent Events(SSE) 구독을 처리하는 컨트롤러입니다.
 * 클라이언트가 SSE를 통해 알림 스트림에 구독할 수 있도록 합니다.
 */
@RestController
@RequiredArgsConstructor
public class NotificationController {


    private final NotificationSubscribeService notificationSubscribeService;
    private final NotificationService notificationService;

    /**
     * 인증된 사용자를 Server-Sent Events(SSE)를 이용한 알림 스트림에 구독시킵니다.
     *
     * @param userDetails 인증된 사용자의 정보로, 구독할 사용자를 식별하는 데 사용됩니다.
     * @param lastEventId 클라이언트가 마지막으로 받은 이벤트의 ID로, 이벤트 재전송 또는 복구에 사용됩니다.
     * @return 클라이언트에게 알림 이벤트를 스트리밍하는 SseEmitter 객체를 반환합니다.
     */
    // produces = "text/event-stream"을 사용하여 SSE 응답 콘텐츠 타입을 지정합니다.
    @GetMapping(value = "/api/v1/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public SseEmitter subscribe(@AuthenticationPrincipal UserDetails userDetails,
                                // Last-Event-ID 헤더는 마지막으로 받은 이벤트부터 이벤트 스트리밍을 재개하는 데 사용됩니다.
                                @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "")
                                String lastEventId) {
        return notificationSubscribeService.subscribe(userDetails.getUsername(), lastEventId);
    }



    @Operation(summary = "전체 알림 조회", description = "특정 회원의 전체 알림을 최신순으로 페이징 조회합니다.")
    @GetMapping(value = "/api/v1/notifications")
    public SuccessResponse<Slice<NotificationResponse>> getNotification(
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails,
            @ParameterObject Pageable pageable
    ) {
        return SuccessResponse.onSuccess(notificationService.getNotifications(userDetails.getMemberId(), pageable));
    }


    @Operation(summary = "읽지 않은 알림 개수 조회", description = "읽지 않은 알림 개수를 조회합니다.")
    @GetMapping(value = "/api/v1/notifications/unread/count")
    public SuccessResponse<NotificationUnreadCountResponse> getNotificationCount (
            @AuthenticationPrincipal CustomSecurityUserDetails userDetails
    ) {
        return SuccessResponse.onSuccess(new NotificationUnreadCountResponse
                (notificationService.getUnreadCount(userDetails.getMemberId())));
    }
}
