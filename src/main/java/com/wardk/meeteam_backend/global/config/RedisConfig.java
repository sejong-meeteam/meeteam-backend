package com.wardk.meeteam_backend.global.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Redis 값 직렬화 전용 Serializer.
     *
     * <p>주의: {@code GenericJackson2JsonRedisSerializer(ObjectMapper)} 생성자는 넘겨받은 mapper를
     * 그대로 사용하며 default typing(@class)을 켜지 않는다. Spring Boot 기본 ObjectMapper에는
     * polymorphic typing이 꺼져 있으므로, 그대로 사용하면 저장 시 타입 정보가 빠지고 읽을 때
     * {@code LinkedHashMap}으로 역직렬화되어 {@code SseEnvelope} 캐시 복원이 깨진다.</p>
     *
     * <p>이를 막기 위해 기존 ObjectMapper의 모듈(JavaTimeModule 등)을 유지한 copy에 default typing을
     * 추가한다. 전역 default typing은 역직렬화 가젯 취약점이 있으므로, 허용 서브타입을 우리 도메인과
     * 표준 라이브러리로 제한한 {@link PolymorphicTypeValidator}를 사용한다.</p>
     */
    @Bean
    public GenericJackson2JsonRedisSerializer redisValueSerializer(ObjectMapper objectMapper) {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.wardk.meeteam_backend.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();

        ObjectMapper redisMapper = objectMapper.copy()
                .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);

        return new GenericJackson2JsonRedisSerializer(redisMapper);
    }

    @Bean
    public RedisTemplate<String, Object> redisObjectTemplate(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer redisValueSerializer
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // key 직렬화기 (문자열)
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        // value 직렬화기 (JSON + 타입정보)
        template.setValueSerializer(redisValueSerializer);
        template.setHashValueSerializer(redisValueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}