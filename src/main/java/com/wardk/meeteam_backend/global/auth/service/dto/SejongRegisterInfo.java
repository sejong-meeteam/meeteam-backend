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
}