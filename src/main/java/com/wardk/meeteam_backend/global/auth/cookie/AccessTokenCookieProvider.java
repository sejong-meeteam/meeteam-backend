package com.wardk.meeteam_backend.global.auth.cookie;

import com.wardk.meeteam_backend.global.config.CookieProperties;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Access Token 쿠키 생성을 담당하는 컴포넌트.
 * 쿠키 설정을 중앙에서 관리하여 일관성을 보장합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessTokenCookieProvider {

    private final CookieProperties cookieProperties;
    private final JwtUtil jwtUtil;

    /**
     * Access Token 쿠키를 생성합니다.
     *
     * @param accessToken Access Token 값
     * @return ResponseCookie 객체
     */
    public ResponseCookie createCookie(String accessToken) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
            .from(JwtUtil.ACCESS_COOKIE_NAME, accessToken)
            .httpOnly(true)
            .secure(cookieProperties.isSecure())
            .path("/")
            .sameSite(cookieProperties.getSameSite())
            .maxAge(jwtUtil.getAccessExpirationTime() / 1000);

        // 도메인이 설정된 경우에만 추가 (로컬 환경에서는 생략)
        if (cookieProperties.getDomain() != null && !cookieProperties.getDomain().isBlank()) {
            builder.domain(cookieProperties.getDomain());
        }

        return builder.build();
    }

    /**
     * 만료된 Access Token 쿠키를 생성합니다. (로그아웃 시 사용)
     *
     * @return 즉시 만료되는 ResponseCookie 객체
     */
    public ResponseCookie createExpiredCookie() {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
            .from(JwtUtil.ACCESS_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(cookieProperties.isSecure())
            .path("/")
            .sameSite(cookieProperties.getSameSite())
            .maxAge(0);

        if (cookieProperties.getDomain() != null && !cookieProperties.getDomain().isBlank()) {
            builder.domain(cookieProperties.getDomain());
        }

        return builder.build();
    }

    /**
     * HttpServletResponse에 Access Token 쿠키를 설정합니다.
     *
     * @param response    HttpServletResponse
     * @param accessToken Access Token 값
     */
    public void addCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie cookie = createCookie(accessToken);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("Access Token 쿠키 설정 완료");
    }

    /**
     * HttpServletResponse에서 Access Token 쿠키를 삭제합니다. (로그아웃 시 사용)
     *
     * @param response HttpServletResponse
     */
    public void deleteCookie(HttpServletResponse response) {
        ResponseCookie cookie = createExpiredCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("Access Token 쿠키 삭제 완료");
    }
}
