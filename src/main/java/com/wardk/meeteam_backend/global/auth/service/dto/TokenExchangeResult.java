package com.wardk.meeteam_backend.global.auth.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenExchangeResult {
    private String accessToken;
    private String refreshToken;
}
