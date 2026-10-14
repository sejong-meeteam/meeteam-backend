package com.wardk.meeteam_backend.acceptance.cucumber.api;

import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 프로젝트 관리 관련 API 호출 클래스
 */
@Component
public class ProjectManagementApi {

    public ExtractableResponse<Response> 팀원_목록_조회(String token, Long projectId) {
        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get("/api/project-members/{projectId}", projectId)
                .then().log().all()
                .extract();
    }

    public ExtractableResponse<Response> 팀원_삭제(String token, Long projectId, Long memberId) {
        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "projectId", projectId,
                        "memberId", memberId
                ))
                .when()
                .post("/api/project-members")
                .then().log().all()
                .extract();
    }

    public ExtractableResponse<Response> 삭제(String token, Long projectId) {
        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .delete("/api/projects/{projectId}", projectId)
                .then().log().all()
                .extract();
    }

    public ExtractableResponse<Response> 목록_조회() {
        return RestAssured.given().log().all()
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get("/api/projects")
                .then().log().all()
                .extract();
    }

    public ExtractableResponse<Response> 모집_상태_토글(String token, Long projectId) {
        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .post("/api/v1/projects/{projectId}/recruitment/toggle", projectId)
                .then().log().all()
                .extract();
    }
}