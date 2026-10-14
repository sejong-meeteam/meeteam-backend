package com.wardk.meeteam_backend.acceptance.cucumber.api;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 회원 관련 API 호출 클래스
 */
@Component
public class MemberAPI {

    /**
     * 나의 프로필 조회
     */
    public ExtractableResponse<Response> 내_프로필_조회_요청(String accessToken) {
        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .when()
                .get("/api/members")
                .then().log().all()
                .extract();
    }

    /**
     * 특정 회원 프로필 조회
     */
    public ExtractableResponse<Response> 프로필_조회_요청(Long memberId) {
        return RestAssured.given().log().all()
                .when()
                .get("/api/members/{memberId}", memberId)
                .then().log().all()
                .extract();
    }

    /**
     * 회원 검색/필터링
     */
    public ExtractableResponse<Response> 회원_검색_요청(List<String> jobFields, List<String> skills) {
        var spec = RestAssured.given().log().all();
        
        if (jobFields != null && !jobFields.isEmpty()) {
            spec.queryParam("jobFields", jobFields);
        }
        if (skills != null && !skills.isEmpty()) {
            spec.queryParam("skills", skills);
        }

        return spec
                .when()
                .get("/api/members/search")
                .then().log().all()
                .extract();
    }

    /**
     * 내 프로필 수정
     */
    public ExtractableResponse<Response> 프로필_수정_요청(
            String accessToken, String name, String birthDate, 
            String gender, List<String> jobPositions, List<String> skills,
            String introduction, String githubUrl, String blogUrl) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("birthDate", birthDate);
        params.put("gender", gender);
        params.put("jobPositions", jobPositions);
        params.put("skills", skills != null ? skills : List.of());
        params.put("introduction", introduction);
        params.put("githubUrl", githubUrl);
        params.put("blogUrl", blogUrl);

        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType("multipart/form-data")
                .multiPart("memberInfo", params, "application/json")
                .when()
                .put("/api/members")
                .then().log().all()
                .extract();
    }
}
