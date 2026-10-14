package com.wardk.meeteam_backend.global.config;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static io.swagger.v3.oas.models.security.SecurityScheme.Type.*;


@OpenAPIDefinition(
        info = @Info(
                title = "Meeteam",
                description = """
                                Api Docs
                        """,
                version = "1.0v"
        ),
        servers = {
                @Server(url = "/", description = "현재 접속한 서버 (자동)")
        }

)
@Configuration
public class SwaggerConfig {


    @Bean
    public OpenAPI openAPI() {
        SecurityScheme apiKey = new SecurityScheme()
                .type(HTTP)
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .scheme("bearer")
                .bearerFormat("JWT");

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("Bearer Token");

        return new OpenAPI()
                .components(new Components().addSecuritySchemes("Bearer Token", apiKey))
                .addSecurityItem(securityRequirement)
                .servers(List.of(
                                new io.swagger.v3.oas.models.servers.Server()
                                        .url("/")
                                        .description("현재 접속한 서버 (자동)")
                        )
                )
                .path("/api/v1/auth/login", createLoginPathItem());
    }

    /**
     * Spring Security Filter 기반 로그인 API를 Swagger에 문서화
     */
    private PathItem createLoginPathItem() {
        // Request Body 스키마 정의
        Schema<?> loginRequestSchema = new Schema<>()
                .type("object")
                .addProperty("email", new Schema<>().type("string").description("이메일").example("meeteam@naver.com"))
                .addProperty("password", new Schema<>().type("string").description("비밀번호").example("qwer1234"))
                .required(List.of("email", "password"));

        // Response 스키마 정의
        Schema<?> loginResponseSchema = new Schema<>()
                .type("object")
                .addProperty("code", new Schema<>().type("string").example("LOGIN_SUCCESS"))
                .addProperty("message", new Schema<>().type("string").example("로그인에 성공하였습니다."))
                .addProperty("data", new Schema<>()
                        .type("object")
                        .addProperty("name", new Schema<>().type("string").example("홍길동"))
                        .addProperty("memberId", new Schema<>().type("integer").format("int64").example(1)));

        Operation loginOperation = new Operation()
                .tags(List.of("AuthController"))
                .summary("로그인")
                .description("""
                        이메일과 비밀번호로 로그인합니다.

                        **응답 헤더:**
                        - `Authorization`: Bearer {accessToken}

                        **응답 쿠키:**
                        - `refreshToken`: JWT Refresh Token (HttpOnly)
                        """)
                .requestBody(new RequestBody()
                        .required(true)
                        .content(new Content()
                                .addMediaType("application/json", new MediaType().schema(loginRequestSchema))))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("로그인 성공")
                                .content(new Content()
                                        .addMediaType("application/json", new MediaType().schema(loginResponseSchema))))
                        .addApiResponse("401", new ApiResponse()
                                .description("인증 실패 (아이디/비밀번호 불일치)")));

        return new PathItem().post(loginOperation);
    }
}
