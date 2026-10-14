package com.wardk.meeteam_backend.global.config;

import com.wardk.meeteam_backend.domain.notification.service.SseEventPublisher;
import com.wardk.meeteam_backend.domain.notification.service.SseEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * SSE 알림의 멀티 인스턴스 전파를 위한 Redis Pub/Sub 구독 구성.
 *
 * <p>{@link RedisMessageListenerContainer}가 {@code sse:events} 채널을 구독하고, 수신 메시지를
 * {@link SseEventSubscriber}로 위임한다. 기존 Redis 인스턴스를 그대로 사용하며 추가 인프라가 필요 없다.</p>
 */
@Configuration
public class RedisPubSubConfig {

    @Bean
    public ChannelTopic sseChannelTopic() {
        return new ChannelTopic(SseEventPublisher.SSE_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer sseMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            SseEventSubscriber sseEventSubscriber,
            ChannelTopic sseChannelTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(sseEventSubscriber, sseChannelTopic);
        return container;
    }
}