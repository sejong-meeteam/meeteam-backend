package com.wardk.meeteam_backend.web.auth.dto.sejong;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SejongLoginResponse {

    private boolean isNewMember;
    private String code;

    public static SejongLoginResponse existingMember() {
        return new SejongLoginResponse(false, null);
    }

    public static SejongLoginResponse newMember(String code) {
        return new SejongLoginResponse(true, code);
    }
}