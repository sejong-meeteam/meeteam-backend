package com.wardk.meeteam_backend.acceptance.cucumber.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 프로젝트 생성 관련 API 호출 클래스
 */
@Component
@RequiredArgsConstructor
public class ProjectCreateApi {

    private final ObjectMapper objectMapper;

    // === API 요청 메서드 ===

    /**
     * 프로젝트 생성 요청 - 기본 정보만 필요한 경우
     */
    public ExtractableResponse<Response> 생성(String token, String projectName, String category,
                                              String jobFieldCode, int recruitmentCount) {
        Map<String, Object> params = 기본_생성_요청(projectName, category, jobFieldCode, recruitmentCount);
        return 생성_요청(token, params);
    }

    /**
     * 프로젝트 생성 요청 - 여러 모집 포지션
     */
    public ExtractableResponse<Response> 생성_여러_포지션(String token, String projectName, String category,
                                                        List<Map<String, Object>> recruitments) {
        Map<String, Object> params = 기본_생성_요청_커스텀_모집(projectName, category, recruitments);
        return 생성_요청(token, params);
    }

    /**
     * 프로젝트 생성 요청 - 마감 방식 지정
     */
    public ExtractableResponse<Response> 생성_마감방식(String token, String deadlineType, LocalDate endDate) {
        Map<String, Object> params = 기본_생성_요청("테스트 프로젝트", "CAPSTONE", "BACKEND", 2);
        params.put("recruitmentDeadlineType", deadlineType);
        if (endDate != null) {
            params.put("endDate", endDate.toString());
        } else {
            params.remove("endDate");
        }
        return 생성_요청(token, params);
    }

    /**
     * 프로젝트 상세 조회
     */
    public ExtractableResponse<Response> 상세조회(Long projectId) {
        return RestAssured.given().log().all()
                .accept("application/json")
                .when()
                .get("/api/v1/projects/{projectId}", projectId)
                .then().log().all()
                .extract();
    }

    // === 요청 데이터 생성 (기본값 포함) ===

    /**
     * 기본 프로젝트 생성 요청 데이터
     */
    public Map<String, Object> 기본_생성_요청(String projectName, String category,
                                            String jobFieldCode, int recruitmentCount) {
        List<Map<String, Object>> recruitments = List.of(
                모집포지션_생성(jobFieldCode, recruitmentCount)
        );
        return 기본_생성_요청_커스텀_모집(projectName, category, recruitments);
    }

    /**
     * 기본 프로젝트 생성 요청 데이터 - 커스텀 모집 포지션
     */
    public Map<String, Object> 기본_생성_요청_커스텀_모집(String projectName, String category,
                                                         List<Map<String, Object>> recruitments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("projectName", projectName);
        params.put("projectCategory", category);
        params.put("description", "테스트 프로젝트 설명입니다.");
        params.put("platformCategory", "WEB");
        params.put("creatorJobPositionCode", "JAVA_SPRING");
        params.put("recruitments", recruitments);
        params.put("recruitmentDeadlineType", "END_DATE");
        params.put("endDate", LocalDate.now().plusMonths(1).toString());
        return params;
    }

    /**
     * 모집 포지션 데이터 생성
     */
    public Map<String, Object> 모집포지션_생성(String jobFieldCode, int recruitmentCount) {
        JobFieldCode fieldCode = JobFieldCode.valueOf(jobFieldCode);
        JobPositionCode positionCode = getDefaultPositionCode(fieldCode);

        Map<String, Object> recruitment = new LinkedHashMap<>();
        recruitment.put("jobFieldCode", fieldCode.name());
        recruitment.put("jobPositionCode", positionCode.name());
        recruitment.put("recruitmentCount", recruitmentCount);
        recruitment.put("techStackIds", getDefaultTechStackIds(fieldCode));
        return recruitment;
    }

    /**
     * 모집 포지션 데이터 생성 - 직군/포지션 직접 지정
     */
    public Map<String, Object> 모집포지션_생성_직접(JobFieldCode fieldCode, JobPositionCode positionCode, int recruitmentCount) {
        Map<String, Object> recruitment = new LinkedHashMap<>();
        recruitment.put("jobFieldCode", fieldCode.name());
        recruitment.put("jobPositionCode", positionCode.name());
        recruitment.put("recruitmentCount", recruitmentCount);
        recruitment.put("techStackIds", getDefaultTechStackIds(fieldCode));
        return recruitment;
    }

    /**
     * 한글 직군/포지션명을 기반으로 모집 포지션 생성
     * @param jobFieldName 직군명 (예: "백엔드", "프론트", "디자인")
     * @param positionName 포지션명 (예: "Java/Spring", "웹 프론트엔드")
     * @param recruitmentCount 모집 인원
     */
    public Map<String, Object> 모집포지션_생성(String jobFieldName, String positionName, int recruitmentCount) {
        JobFieldCode fieldCode = 직군명_파싱(jobFieldName);
        JobPositionCode positionCode = 포지션명_파싱(positionName);
        return 모집포지션_생성_직접(fieldCode, positionCode, recruitmentCount);
    }

    /**
     * 한글 직군명을 JobFieldCode로 변환
     */
    public JobFieldCode 직군명_파싱(String fieldName) {
        return switch (fieldName) {
            case "백엔드" -> JobFieldCode.BACKEND;
            case "프론트", "프론트엔드" -> JobFieldCode.FRONTEND;
            case "디자인" -> JobFieldCode.DESIGN;
            case "기획" -> JobFieldCode.PLANNING;
            case "AI" -> JobFieldCode.AI;
            case "인프라", "인프라/운영" -> JobFieldCode.INFRA_OPERATION;
            default -> throw new IllegalArgumentException("알 수 없는 직군: " + fieldName);
        };
    }

    /**
     * 한글 포지션명을 JobPositionCode로 변환
     */
    public JobPositionCode 포지션명_파싱(String positionName) {
        return switch (positionName) {
            case "Java/Spring" -> JobPositionCode.JAVA_SPRING;
            case "Kotlin/Spring" -> JobPositionCode.KOTLIN_SPRING;
            case "웹 프론트엔드" -> JobPositionCode.WEB_FRONTEND;
            case "UI/UX 디자이너" -> JobPositionCode.UI_UX_DESIGNER;
            case "LLM" -> JobPositionCode.LLM;
            default -> throw new IllegalArgumentException("알 수 없는 포지션: " + positionName);
        };
    }

    /**
     * "직군 - 포지션" 형식의 문자열에서 JobPositionCode 추출
     */
    public JobPositionCode 포지션_문자열_파싱(String positionString) {
        String[] parts = positionString.split(" - ");
        String positionName = parts.length > 1 ? parts[1].trim() : parts[0].trim();
        return 포지션명_파싱(positionName);
    }

    /**
     * "직군 - 포지션" 형식의 문자열에서 JobFieldCode 추출
     */
    public JobFieldCode 직군_문자열_파싱(String positionString) {
        String[] parts = positionString.split(" - ");
        if (parts.length < 2) {
            throw new IllegalArgumentException("포지션 형식이 올바르지 않습니다: " + positionString);
        }
        return 직군명_파싱(parts[0].trim());
    }

    // === Validation 테스트용 요청 데이터 ===

    /**
     * 프로젝트명 누락 요청
     */
    public ExtractableResponse<Response> 프로젝트명_누락_요청(String token) {
        Map<String, Object> params = 기본_생성_요청("", "CAPSTONE", "BACKEND", 2);
        params.remove("projectName");
        return 생성_요청(token, params);
    }

    /**
     * 모집 포지션 누락 요청
     */
    public ExtractableResponse<Response> 모집포지션_누락_요청(String token) {
        Map<String, Object> params = 기본_생성_요청("테스트 프로젝트", "CAPSTONE", "BACKEND", 2);
        params.put("recruitments", List.of());
        return 생성_요청(token, params);
    }

    /**
     * 마감 방식 누락 요청
     */
    public ExtractableResponse<Response> 마감방식_누락_요청(String token) {
        Map<String, Object> params = 기본_생성_요청("테스트 프로젝트", "CAPSTONE", "BACKEND", 2);
        params.remove("recruitmentDeadlineType");
        return 생성_요청(token, params);
    }

    /**
     * END_DATE 방식인데 마감일 누락
     */
    public ExtractableResponse<Response> 마감일_누락_요청(String token) {
        Map<String, Object> params = 기본_생성_요청("테스트 프로젝트", "CAPSTONE", "BACKEND", 2);
        params.put("recruitmentDeadlineType", "END_DATE");
        params.remove("endDate");
        return 생성_요청(token, params);
    }

    /**
     * RECRUITMENT_COMPLETED 방식인데 마감일 포함
     */
    public ExtractableResponse<Response> 모집완료_마감일_포함_요청(String token) {
        Map<String, Object> params = 기본_생성_요청("테스트 프로젝트", "CAPSTONE", "BACKEND", 2);
        params.put("recruitmentDeadlineType", "RECRUITMENT_COMPLETED");
        params.put("endDate", LocalDate.now().plusMonths(1).toString());
        return 생성_요청(token, params);
    }

    /**
     * 비인증 프로젝트 생성 요청
     */
    public ExtractableResponse<Response> 비인증_생성_요청() {
        Map<String, Object> params = 기본_생성_요청("테스트 프로젝트", "CAPSTONE", "BACKEND", 2);
        return 생성_요청(null, params);
    }

    // === 내부 헬퍼 ===

    private ExtractableResponse<Response> 생성_요청(String token, Map<String, Object> params) {
        var spec = RestAssured.given().log().all()
                .accept("application/json")
                .contentType("multipart/form-data")
                .multiPart("request", "request.json", toJson(params).getBytes(), "application/json");

        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return spec
                .when()
                .post("/api/v1/projects")
                .then().log().all()
                .extract();
    }

    /**
     * 직군별 기본 직무 코드 반환
     */
    private JobPositionCode getDefaultPositionCode(JobFieldCode fieldCode) {
        return switch (fieldCode) {
            case BACKEND -> JobPositionCode.JAVA_SPRING;
            case FRONTEND -> JobPositionCode.WEB_FRONTEND;
            case DESIGN -> JobPositionCode.UI_UX_DESIGNER;
            case PLANNING -> JobPositionCode.PRODUCT_MANAGER;
            case AI -> JobPositionCode.MACHINE_LEARNING;
            case INFRA_OPERATION -> JobPositionCode.DEVOPS_ARCHITECT;
        };
    }

    /**
     * 직군별 기본 기술스택 ID 반환 (data-h2.sql 기준)
     */
    private List<Long> getDefaultTechStackIds(JobFieldCode fieldCode) {
        return switch (fieldCode) {
            case BACKEND -> List.of(30L, 33L, 37L);      // Java, Spring Boot, JPA
            case FRONTEND -> List.of(10L, 11L, 12L);    // React.js, TypeScript, Next.js
            case DESIGN -> List.of(3L, 5L);             // Figma, Zeplin
            case PLANNING -> List.of(1L, 2L, 3L);       // Notion, Jira, Figma
            case AI -> List.of(31L, 60L, 61L);          // Python, PyTorch, TensorFlow
            case INFRA_OPERATION -> List.of(45L, 46L, 70L); // Docker, AWS, Kubernetes
        };
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("프로젝트 요청 본문 직렬화에 실패했습니다.", e);
        }
    }
}