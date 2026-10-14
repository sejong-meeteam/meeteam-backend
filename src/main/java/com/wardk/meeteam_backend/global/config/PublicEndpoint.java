package com.wardk.meeteam_backend.global.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인증 없이 접근 가능한 Public 엔드포인트 목록.
 * 새로운 Public API 추가 시 여기에 등록하세요.
 */
@Getter
@RequiredArgsConstructor
public enum PublicEndpoint {

    // ===== Swagger / API Docs =====
    SWAGGER_WEBJARS("*", "/webjars/**"),
    SWAGGER_RESOURCES("*", "/swagger-resources/**"),
    API_DOCS("*", "/v3/api-docs/**"),
    SWAGGER_UI("*", "/swagger-ui/**"),
    SWAGGER_HTML("*", "/swagger-ui.html"),
    DOCS("*", "/docs/**"),

    // ===== Actuator / Monitoring =====
    ACTUATOR("*", "/actuator/**"),

    // ===== Static Resources =====
    DEFAULT_CSS("*", "/default-ui.css"),
    FAVICON("*", "/favicon.ico"),
    INDEX_HTML("*", "/index.html"),
    ROOT("*", "/"),
    UPLOADS("*", "/uploads/**"),

    // ===== Auth =====
    LOGIN("*", "/api/login"),
    AUTH_V1("*", "/api/v1/auth/**"),
    REGISTER("*", "/api/register"),

    // ===== Files =====
    FILES_V1("*", "/api/v1/files/**"),

    // ===== Project (Public) =====
    PROJECT_REGISTER("*", "/api/project/register"),
    PROJECT_CONDITION("GET", "/api/projects/condition"),
    PROJECT_SEARCH_V1("GET", "/api/v1/projects/search"),
    PROJECT_DETAIL_V1("GET", "/api/v1/projects/*"),
    PROJECT_QNA_V1("GET", "/api/v1/projects/*/qna"),
    PROJECT_DETAIL_LEGACY("GET", "/api/projects/*"),
    PROJECT_DETAIL_V2("GET", "/api/projects/V2/*"),

    // ===== Main Page =====
    MAIN_PROJECTS("GET", "/api/v1/main/projects"),
    MAIN_MEMBERS("GET", "/api/v1/main/members"),

    // ===== Member (Public) =====
    MEMBER_SEARCH_V1("GET", "/api/v1/members/search"),
    MEMBER_DETAIL_V1("GET", "/api/v1/members/*"),
    MEMBER_ALL("GET", "/api/members/all"),
    MEMBER_SEARCH("GET", "/api/members/search"),

    // ===== Job =====
    JOB_OPTIONS("GET", "/api/v1/jobs/options"),

    // ===== Like =====
    PROJECT_LIKE("GET", "/api/project/like/*");

    private final String method;
    private final String uri;

    /**
     * 모든 HTTP Method 허용 여부
     */
    public boolean isAllMethods() {
        return "*".equals(method);
    }
}