package com.wardk.meeteam_backend.web.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * SSE 전송 및 재연결 캐시에 사용하는 이벤트 봉투(envelope).
 *
 * <p>Redis 캐시 round-trip을 위해 Jackson 역직렬화가 가능해야 한다.
 * {@code final} 필드 + 빌더만으로는 컴파일 {@code -parameters} 플래그에 의존하므로,
 * {@link JsonCreator}로 생성자 바인딩을 명시하여 빌드 설정과 무관하게 안정적으로 복원되도록 한다.</p>
 */
@Getter
public class SseEnvelope<T> {

    private final NotificationType type;
    private final T data;
    private final LocalDateTime createdAt;

    @Builder
    @JsonCreator
    public SseEnvelope(
            @JsonProperty("type") NotificationType type,
            @JsonProperty("data") T data,
            @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.type = type;
        this.data = data;
        this.createdAt = createdAt;
    }
}