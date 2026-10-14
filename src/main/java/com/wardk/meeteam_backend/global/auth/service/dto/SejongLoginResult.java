package com.wardk.meeteam_backend.global.auth.service.dto;

import lombok.Getter;

/**
 * 세종대 포털 로그인 결과
 * - 기존 회원: isNewMember=false, tokens 존재
 * - 신규 회원: isNewMember=true, code 존재
 */
@Getter
public class SejongLoginResult {

    private final boolean isNewMember;
    private final String accessToken;
    private final String refreshToken;
    private final String code;

    private SejongLoginResult(boolean isNewMember, String accessToken, String refreshToken, String code) {
        this.isNewMember = isNewMember;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.code = code;
    }

    public static SejongLoginResult existingMember(String accessToken, String refreshToken) {
        return new SejongLoginResult(false, accessToken, refreshToken, null);
    }

    public static SejongLoginResult newMember(String code) {
        return new SejongLoginResult(true, null, null, code);
    }
}