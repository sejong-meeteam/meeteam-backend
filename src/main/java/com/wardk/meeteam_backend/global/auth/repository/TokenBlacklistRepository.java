package com.wardk.meeteam_backend.global.auth.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * AccessToken 블랙리스트 관리를 위한 Redis Repository
 * 로그아웃된 토큰을 만료 시간까지 블랙리스트에 저장하여 재사용을 방지합니다.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistRepository {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:token:";

    /**
     * 토큰을 블랙리스트에 추가
     *
     * @param jti 토큰의 고유 식별자 (JWT ID)
     * @param remainingTimeMs 토큰의 남은 만료 시간 (밀리초)
     */
    public void addToBlacklist(String jti, long remainingTimeMs) {
        if (jti == null || jti.isBlank()) {
            log.warn("JTI가 null이거나 비어있어 블랙리스트에 추가하지 않습니다.");
            return;
        }

        if (remainingTimeMs <= 0) {
            log.debug("토큰이 이미 만료되어 블랙리스트에 추가하지 않습니다. JTI: {}", jti);
            return;
        }

        String key = BLACKLIST_PREFIX + jti;
        Duration ttl = Duration.ofMillis(remainingTimeMs);

        stringRedisTemplate.opsForValue().set(key, "1", ttl);
        log.info("토큰이 블랙리스트에 추가되었습니다. JTI: {}, TTL: {}ms", jti, remainingTimeMs);
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인
     *
     * @param jti 토큰의 고유 식별자 (JWT ID)
     * @return 블랙리스트에 있으면 true, 없으면 false
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }

        String key = BLACKLIST_PREFIX + jti;
        Boolean exists = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 토큰을 블랙리스트에서 제거 (테스트용)
     *
     * @param jti 토큰의 고유 식별자 (JWT ID)
     */
    public void removeFromBlacklist(String jti) {
        if (jti == null || jti.isBlank()) {
            return;
        }

        String key = BLACKLIST_PREFIX + jti;
        stringRedisTemplate.delete(key);
        log.debug("토큰이 블랙리스트에서 제거되었습니다. JTI: {}", jti);
    }
}
