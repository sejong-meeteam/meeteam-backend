package com.wardk.meeteam_backend.web.auth.dto.register;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RegisterDescriptionRequest {

    @Schema(description = "자기소개", example = "안녕하세요 , 백엔드 개발자 (Spring)입니다.")
    private String introduce;
}
