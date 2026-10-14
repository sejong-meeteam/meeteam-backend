package com.wardk.meeteam_backend.web.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * Redis Pub/Sub 채널로 인스턴스 간 전파되는 SSE 메시지.
 *
 * <p>어느 수신자({@code receiverId})에게, 어떤 이벤트 ID({@code eventId})로,
 * 어떤 봉투({@code envelope})를 전송할지를 담는다. 메시지를 받은 각 인스턴스는 자신의 로컬 emitter 맵에
 * 해당 수신자가 있을 때만 전송한다.</p>
 *
 * <p>Redis JSON round-trip을 위해 default typing(@class) 대상이 되도록 final이 아닌 일반 클래스로 두고,
 * {@link JsonCreator}로 생성자 바인딩을 명시한다.</p>
 */
@Getter
public class SsePublishMessage {

    private final Long receiverId;
    private final String eventId;
    private final SseEnvelope<Object> envelope;

    @Builder
    @JsonCreator
    public SsePublishMessage(
            @JsonProperty("receiverId") Long receiverId,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("envelope") SseEnvelope<Object> envelope) {
        this.receiverId = receiverId;
        this.eventId = eventId;
        this.envelope = envelope;
    }
}