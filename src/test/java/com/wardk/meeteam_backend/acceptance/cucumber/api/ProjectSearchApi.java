package com.wardk.meeteam_backend.acceptance.cucumber.api;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProjectSearchApi {

    public Response getProjectList() {
        return RestAssured.given()
                .accept("application/json")
                .when()
                .get("/api/projects")
                .then()
                .extract()
                .response();
    }

    public Response searchCondition(Map<String, Object> queryParams) {
        var spec = RestAssured.given()
                .accept("application/json");

        queryParams.forEach(spec::queryParam);

        return spec.when()
                .get("/api/projects/condition")
                .then()
                .extract()
                .response();
    }

}
