package com.wardk.meeteam_backend.web.auth.dto.sejong;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SejongLoginRequest {

    @NotBlank(message = "학번을 입력해주세요.")
    private String studentId;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;
}