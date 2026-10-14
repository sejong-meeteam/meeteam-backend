package com.wardk.meeteam_backend.global.auth.service.dto;

/**
 * 세종대 포털 로그인 요청을 서비스 레이어로 전달하기 위한 Command 객체.
 */
public record SejongLoginCommand(
        String studentId,
        String password
) {
}