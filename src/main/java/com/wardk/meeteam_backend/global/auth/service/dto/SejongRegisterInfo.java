package com.wardk.meeteam_backend.global.auth.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 세종대 포털 인증 후 신규 회원 정보를 Redis에 임시 저장하기 위한 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SejongRegisterInfo {
    private String studentId;
    private String name;
    private String department;

    /** Legacy codes deserialize but cannot register without a freshly verified profile. */
    public SejongRegisterInfo(String studentId) {
        this.studentId = studentId;
    }

    public com.wardk.meeteam_backend.global.auth.client.SejongPortalProfile requireVerifiedProfile() {
        try {
            return new com.wardk.meeteam_backend.global.auth.client.SejongPortalProfile(studentId, name, department);
        } catch (IllegalArgumentException e) {
            throw new com.wardk.meeteam_backend.global.exception.CustomException(
                    com.wardk.meeteam_backend.global.response.ErrorCode.INVALID_SEJONG_CODE);
        }
    }

    @Override public String toString() { return "SejongRegisterInfo[redacted]"; }
}