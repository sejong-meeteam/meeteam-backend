package com.wardk.meeteam_backend.web.auth.dto.sejong;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SejongLoginResponse {

    private boolean isNewMember;
    private String code;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private com.wardk.meeteam_backend.global.auth.client.SejongPortalProfile verifiedProfile;

    public SejongLoginResponse(boolean isNewMember, String code) {
        this(isNewMember, code, null);
    }

    public static SejongLoginResponse existingMember() {
        return new SejongLoginResponse(false, null);
    }

    public static SejongLoginResponse newMember(String code) {
        return new SejongLoginResponse(true, code);
    }

    public static SejongLoginResponse newMember(String code,
            com.wardk.meeteam_backend.global.auth.client.SejongPortalProfile verifiedProfile) {
        return new SejongLoginResponse(true, code, verifiedProfile);
    }
}