package com.wardk.meeteam_backend.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 쿠키 관련 설정을 외부화하여 관리하는 Properties 클래스.
 * 환경별로 다른 도메인 설정이 가능합니다.
 *
 * <p>사용 예시 (application.yml):
 * <pre>
 * app:
 *   cookie:
 *     domain: .meeteam.alom-sejong.com  # prod
 *     secure: true
 *     same-site: None
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.cookie")
@Getter
@Setter
public class CookieProperties {

    /**
     * 쿠키 도메인 설정.
     * - prod: ".meeteam.alom-sejong.com" (서브도메인 간 공유)
     * - local: null 또는 빈 문자열 (localhost)
     */
    private String domain;

    /**
     * HTTPS 환경에서만 쿠키 전송 여부.
     * 기본값: true (보안 강화)
     */
    private boolean secure = true;

    /**
     * SameSite 속성 설정.
     * - "None": CORS 환경에서도 쿠키 전송 가능 (secure=true 필수)
     * - "Lax": 기본값, 일부 cross-site 요청에서 쿠키 전송
     * - "Strict": 같은 사이트에서만 쿠키 전송
     */
    private String sameSite = "None";
}