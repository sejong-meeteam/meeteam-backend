package com.wardk.meeteam_backend.web.auth.dto.sejong;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wardk.meeteam_backend.domain.member.entity.Gender;
import com.wardk.meeteam_backend.web.auth.dto.register.MemberJobPositionRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonPropertyOrder({"code", "name", "birthDate", "gender", "jobPositions", "githubUrl", "blogUrl"})
public class SejongRegisterRequest {

    @Schema(description = "세종대 로그인 시 발급받은 일회용 코드", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "인증 코드를 입력해주세요")
    private String code;

    @Schema(description = "이름", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "이름을 입력해주세요")
    private String name;

    @Schema(description = "생년월일", example = "2000-01-01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "생년월일을 입력해주세요")
    private LocalDate birthDate;

    @Schema(description = "성별", example = "MALE", allowableValues = {"MALE", "FEMALE"})
    @NotNull(message = "성별을 선택해주세요")
    private Gender gender;

    @NotEmpty(message = "관심분야를 최소 1개 이상 선택해주세요")
    @Valid
    private List<MemberJobPositionRequest> jobPositions = new ArrayList<>();

    @Schema(description = "GitHub URL", example = "https://github.com/username")
    private String githubUrl;

    @Schema(description = "블로그 URL", example = "https://blog.example.com")
    private String blogUrl;
}