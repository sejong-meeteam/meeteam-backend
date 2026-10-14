package com.wardk.meeteam_backend.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 애플리케이션 시작 시 Warm-up 수행.
 *
 * <p>실제 로그인 API를 5회 호출하여 전체 요청 플로우를 워밍업합니다:</p>
 * <ul>
 *   <li>Spring Security 필터 체인</li>
 *   <li>AOP 프록시 초기화</li>
 *   <li>세종대 포털 커넥션 풀</li>
 *   <li>DB 커넥션 풀</li>
 *   <li>JWT 생성 로직</li>
 * </ul>
 */
@Slf4j
@Component
public class WarmUpRunner implements ApplicationRunner {

    private static final int WARMUP_COUNT = 5;
    private static final String STUDENT_ID = "21013220";
    private static final String PASSWORD = "19980611";

    @Value("${app.warmup.enabled:true}")
    private boolean warmupEnabled;

    @Value("${server.port:8080}")
    private int serverPort;

    @Override
    public void run(ApplicationArguments args) {
        if (!warmupEnabled) {
            log.info("=== Warm-up 비활성화됨 (app.warmup.enabled=false) ===");
            return;
        }

        log.info("=== 애플리케이션 Warm-up 시작 ({}회) ===", WARMUP_COUNT);
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= WARMUP_COUNT; i++) {
            long reqStart = System.currentTimeMillis();
            performWarmupCall(i);
            long reqElapsed = System.currentTimeMillis() - reqStart;
            log.info("Warm-up {}/{} 완료 ({}ms)", i, WARMUP_COUNT, reqElapsed);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("=== 애플리케이션 Warm-up 완료 (총 {}ms) ===", elapsed);
    }

    private void performWarmupCall(int attempt) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:" + serverPort + "/api/v1/auth/login/sejong";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestJson = String.format(
                "{\"studentId\": \"%s\", \"password\": \"%s\"}",
                STUDENT_ID, PASSWORD
        );

        HttpEntity<String> requestEntity = new HttpEntity<>(requestJson, headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        } catch (Exception e) {
            log.debug("Warm-up {} 응답: {}", attempt, e.getMessage());
        }
    }
}