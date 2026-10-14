package com.wardk.meeteam_backend.acceptance.cucumber.api;

import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * 알림 관련 API 호출 클래스
 */
@Component
public class NotificationApi {

    public ExtractableResponse<Response> 조회(String token) {
        return RestAssured.given().log().all()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .queryParam("page", 0)
                .queryParam("size", 100)
                .when()
                .get("/api/notifications")
                .then().log().all()
                .extract();
    }
}