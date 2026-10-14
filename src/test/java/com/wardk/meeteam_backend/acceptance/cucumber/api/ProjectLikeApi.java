package com.wardk.meeteam_backend.acceptance.cucumber.api;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class ProjectLikeApi {

    public Response toggleLike(Long projectId, String accessToken) {
        var spec = RestAssured.given()
                .accept("application/json");

        if (accessToken != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }

        return spec.when()
                .post("/api/v1/project/like/{projectId}", projectId)
                .then()
                .extract()
                .response();
    }

    public Response likeStatus(Long projectId, String accessToken) {
        var spec = RestAssured.given()
                .accept("application/json");

        if (accessToken != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }

        return spec.when()
                .get("/api/v1/project/like/{projectId}", projectId)
                .then()
                .extract()
                .response();
    }

}
