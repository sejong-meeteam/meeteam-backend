package com.wardk.meeteam_backend.global.config;

import lombok.Getter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;

/**
 * Public 엔드포인트 관리.
 * PublicEndpoint enum에서 정의된 경로들을 Security 설정에 제공.
 */
@Component
@Getter
public class SecurityUrls {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 주어진 URI와 HTTP Method가 Public 엔드포인트인지 확인
     */
    public boolean isWhitelisted(String uri, String method) {
        return Arrays.stream(PublicEndpoint.values())
                .anyMatch(endpoint -> {
                    boolean uriMatches = pathMatcher.match(endpoint.getUri(), uri);
                    boolean methodMatches = endpoint.isAllMethods() ||
                            endpoint.getMethod().equalsIgnoreCase(method);
                    return uriMatches && methodMatches;
                });
    }

    /**
     * URI만으로 Public 엔드포인트 확인 (모든 HTTP Method)
     */
    public boolean isWhitelisted(String uri) {
        return isWhitelisted(uri, "*");
    }

    /**
     * Spring Security용 RequestMatcher 배열 반환
     */
    public RequestMatcher[] getRequestMatchers() {
        return Arrays.stream(PublicEndpoint.values())
                .map(endpoint -> {
                    if (endpoint.isAllMethods()) {
                        return new AntPathRequestMatcher(endpoint.getUri());
                    } else {
                        HttpMethod httpMethod = HttpMethod.valueOf(endpoint.getMethod().toUpperCase());
                        return new AntPathRequestMatcher(endpoint.getUri(), httpMethod.name());
                    }
                })
                .toArray(RequestMatcher[]::new);
    }
}