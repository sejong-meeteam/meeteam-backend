package com.wardk.meeteam_backend.global.auth.filter;



import com.wardk.meeteam_backend.global.auth.repository.TokenBlacklistRepository;
import com.wardk.meeteam_backend.global.auth.service.CustomUserDetailsService;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.global.config.SecurityUrls;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final SecurityUrls securityUrls; // 화이트리스트 경로 관리 클래스
    private final CustomUserDetailsService customUserDetailsService;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 인증 생략 경로
        String uri = request.getRequestURI();
        String method = request.getMethod();
        log.debug("JWT 필터 처리 중인 URI: {}, Method: {}", uri, method);

        // 화이트리스트 경로는 JWT 인증을 건너뛰지만, 토큰이 있으면 사용자 정보를 설정
        if (securityUrls.isWhitelisted(uri, method)) {
            log.debug("화이트리스트 경로로 인증 생략: {} {}", method, uri);

            // 화이트리스트 경로에서도 토큰이 있으면 사용자 정보를 SecurityContext에 설정
            setUserDetailsIfTokenExists(request);

            filterChain.doFilter(request, response);
            return;
        }

        // request에서 Access Token 추출 (Authorization 헤더 우선, 쿠키 fallback)
        String token = extractAccessToken(request);

        // 토큰 검증
        if (token == null) {
            log.warn("인증 필요 경로에 토큰이 없습니다: {} {}", method, uri);
            sendUnauthorizedResponse(response, "토큰이 존재하지 않습니다.");
            return;
        }

        log.debug("토큰이 존재합니다");

        // 토큰 처리 및 사용자 정보 설정
        if (processTokenAndSetUserDetails(token)) {
            filterChain.doFilter(request, response);
        } else {
            // 토큰이 유효하지 않음 (만료, 블랙리스트, 탈퇴 회원 등)
            sendUnauthorizedResponse(response, "유효하지 않은 토큰입니다.");
        }
    }

    /**
     * 401 Unauthorized 응답을 반환합니다.
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        ErrorCode ec = ErrorCode.UNAUTHORIZED;
        ErrorResponse body = ErrorResponse.getResponse(ec.getCode(), message);

        response.setStatus(ec.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        new ObjectMapper().writeValue(response.getWriter(), body);
    }

    /**
     * 화이트리스트 경로에서 토큰이 있으면 사용자 정보를 설정
     */
    private void setUserDetailsIfTokenExists(HttpServletRequest request) {
        try {
            String token = extractAccessToken(request);
            if (token != null) {
                // 토큰이 유효하면 사용자 정보 설정
                if (!jwtUtil.isExpired(token)) {
                    processTokenAndSetUserDetails(token);
                    log.debug("화이트리스트 경로에서 토큰 기반 사용자 정보 설정 완료");
                }
            }
        } catch (Exception e) {
            // 화이트리스트 경로에서는 토큰 파싱 실패해도 계속 진행
            log.debug("화이트리스트 경로에서 토큰 파싱 실패, 익명 사용자로 진행: {}", e.getMessage());
        }
    }

    /**
     * Request에서 Access Token 추출
     * 우선순위: Authorization 헤더 > 쿠키
     */
    private String extractAccessToken(HttpServletRequest request) {
        // 1. Authorization 헤더에서 먼저 확인
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        // 2. 쿠키에서 확인
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (JwtUtil.ACCESS_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    /**
     * 토큰을 처리하고 사용자 정보를 SecurityContext에 설정
     */
    private boolean processTokenAndSetUserDetails(String token) {
        try {
            // 토큰 소멸 시간 검증
            if (jwtUtil.isExpired(token)) {
                log.debug("토큰이 만료됨");
                return false;
            }

            // 블랙리스트 체크 (로그아웃된 토큰인지 확인)
            String jti = jwtUtil.getJti(token);
            if (jti != null && tokenBlacklistRepository.isBlacklisted(jti)) {
                log.warn("블랙리스트에 등록된 토큰입니다. JTI: {}", jti);
                return false;
            }

            // 토큰에서 username과 memberId 획득
            String email = jwtUtil.getUsername(token);
            Long memberId = jwtUtil.getMemberId(token);

            // UserDetailsService를 통해 DB 에서 완전한 사용자 정보를 조회합니다.
            CustomSecurityUserDetails customSecurityUserDetails = customUserDetailsService.loadUserByUsername(email);

            // 스프링 시큐리티 인증 토큰 생성
            Authentication authToken = new UsernamePasswordAuthenticationToken(
                    customSecurityUserDetails, null, customSecurityUserDetails.getAuthorities());

            // 세션에 사용자 등록
            SecurityContextHolder.getContext().setAuthentication(authToken);

            log.debug("사용자 정보 SecurityContext에 설정 완료: {}", email);
            return true;

        } catch (Exception e) {
            log.error("토큰 처리 중 오류 발생: {}", e.getMessage());
            return false;
        }
    }
}
