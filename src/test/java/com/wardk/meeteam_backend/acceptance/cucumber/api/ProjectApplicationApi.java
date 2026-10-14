package com.wardk.meeteam_backend.acceptance.cucumber.api;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 프로젝트 지원 관련 API 호출 클래스
 */
@Component
public class ProjectApplicationApi {

    public ExtractableResponse<Response> 지원(String token, Long projectId, JobPositionCode positionCode) {
        return 지원(token, projectId, positionCode, "테스트 지원 동기입니다.");
    }

    public ExtractableResponse<Response> 지원(String token, Long projectId, JobPositionCode positionCode, String motivation) {
        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "jobPositionCode", positionCode.name(),
                        "motivation", motivation
                ))
                .when()
                .post("/api/v1/projects/{projectId}/application", projectId)
                .then().log().all()
                .extract();
    }

    public ExtractableResponse<Response> 지원_상세_조회(String token, Long projectId, Long applicationId) {
        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .when()
                .get("/api/v1/projects/{projectId}/applications/{applicationId}", projectId, applicationId)
                .then().log().all()
                .extract();
    }

    public ExtractableResponse<Response> 지원자_목록_조회(String token, Long projectId) {
        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .when()
                .get("/api/v1/projects/{projectId}/applications", projectId)
                .then().log().all()
                .extract();
    }

    public ExtractableResponse<Response> 승인(String token, Long projectId, Long applicationId) {
        return 지원_결정(token, projectId, applicationId, ApplicationStatus.ACCEPTED);
    }

    public ExtractableResponse<Response> 거절(String token, Long projectId, Long applicationId) {
        return 지원_결정(token, projectId, applicationId, ApplicationStatus.REJECTED);
    }

    private ExtractableResponse<Response> 지원_결정(String token, Long projectId, Long applicationId, ApplicationStatus decision) {
        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "applicationId", applicationId,
                        "decision", decision.name()
                ))
                .when()
                .post("/api/v1/projects/{projectId}/applications/{applicationId}/decision", projectId, applicationId)
                .then().log().all()
                .extract();
    }
}