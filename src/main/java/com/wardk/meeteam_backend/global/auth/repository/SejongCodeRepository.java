package com.wardk.meeteam_backend.global.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.global.auth.service.dto.SejongRegisterInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 세종대 포털 인증 일회용 코드 관리를 위한 Redis Repository.
 * 세종대 포털 인증 후 회원가입 시 학번을 임시 저장하고 일회용 코드로 교환합니다.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class SejongCodeRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "sejong:code:";
    private static final Duration REGISTER_TTL = Duration.ofMinutes(10);

    /**
     * 세종대 포털 인증 후 신규 회원 정보를 Redis에 저장하고 일회용 코드(UUID) 반환.
     *
     * @param info 세종대 신규 회원 정보 (학번)
     * @return 일회용 UUID 코드
     */
    public String saveRegisterInfo(SejongRegisterInfo info) {
        String code = UUID.randomUUID().toString();
        String key = KEY_PREFIX + code;
        try {
            String json = objectMapper.writeValueAsString(info);
            stringRedisTemplate.opsForValue().set(key, json, REGISTER_TTL);
            log.info("세종대 신규 회원 정보 저장 완료. code: {}, TTL: {}분", code, REGISTER_TTL.toMinutes());
        } catch (JsonProcessingException e) {
            log.error("세종대 신규 회원 정보 직렬화 실패: {}", e.getMessage());
            throw new RuntimeException("세종대 정보 저장 실패", e);
        }
        return code;
    }

    /**
     * 일회용 코드로 세종대 신규 회원 정보를 조회하고 즉시 삭제 (일회성 보장).
     *
     * @param code 일회용 UUID 코드
     * @return 세종대 신규 회원 정보 (없으면 empty)
     */
    public Optional<SejongRegisterInfo> consumeRegisterInfo(String code) {
        String key = KEY_PREFIX + code;
        String json = stringRedisTemplate.opsForValue().getAndDelete(key);
        if (json == null) {
            log.warn("세종대 신규 회원 정보를 찾을 수 없습니다. code: {}", code);
            return Optional.empty();
        }
        try {
            SejongRegisterInfo info = objectMapper.readValue(json, SejongRegisterInfo.class);
            log.info("세종대 신규 회원 정보 조회 및 삭제 완료. code: {}", code);
            return Optional.of(info);
        } catch (JsonProcessingException e) {
            log.error("세종대 신규 회원 정보 역직렬화 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
