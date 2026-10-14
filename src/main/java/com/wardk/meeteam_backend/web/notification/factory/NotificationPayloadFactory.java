package com.wardk.meeteam_backend.web.notification.factory;


import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.Payload;
import com.wardk.meeteam_backend.web.notification.strategy.NotificationPayloadStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 알림 타입에 따라 적절한 Payload를 생성하는 팩토리.
 * Strategy 패턴을 사용하여 알림 타입별 Payload 생성 로직을 분리.
 */
@Component
public class NotificationPayloadFactory {

    private final Map<NotificationType, NotificationPayloadStrategy> strategies;

    public NotificationPayloadFactory(List<NotificationPayloadStrategy> list) {
        this.strategies = list.stream()
                .collect(Collectors.toMap(
                        NotificationPayloadStrategy::getType,
                        strategy -> strategy
                ));
    }

    /**
     * NotificationContext를 기반으로 Payload 생성.
     * Context는 이미 Service 레이어에서 필요한 데이터가 enriched된 상태여야 함.
     */
    public Payload create(NotificationType type, NotificationContext context) {
        NotificationPayloadStrategy strategy = strategies.get(type);

        if (strategy == null) {
            throw new CustomException(ErrorCode.NO_MATCHING_TYPE);
        }

        return strategy.create(context);
    }
}
