package com.wardk.meeteam_backend.global.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SuccessCode {

    //가장 일반적인 응답
    _OK(HttpStatus.OK,"COMMON200", "요청에 성공했습니다."),
    _LOGIN_SUCCESS(HttpStatus.OK, "LOGIN200", "로그인에 성공했습니다"),
    _TOKEN_EXCHANGE_SUCCESS(HttpStatus.OK, "TOKEN200", "토큰 교환에 성공했습니다."),

    // PROJECT MEMBER
    _PROJECT_MEMBER_LISTED(HttpStatus.OK, "PROJECT_MEMBER200", "프로젝트 멤버 목록이 조회되었습니다."),
    _PROJECT_MEMBER_DELETED(HttpStatus.OK, "PROJECT_MEMBER200", "프로젝트 멤버가 삭제되었습니다."),
    _PROJECT_MEMBER_ROLE_UPDATED(HttpStatus.OK, "PROJECT_MEMBER200", "프로젝트 멤버의 역할이 변경되었습니다."),
    _PROJECT_OWNER_UPDATED(HttpStatus.OK, "PROJECT_MEMBER200", "프로젝트 관리자가 변경되었습니다."),
    _PROJECT_MEMBER_WITHDREW(HttpStatus.OK, "PROJECT_MEMBER200", "프로젝트 멤버가 탈퇴되었습니다."),

    // PROJECT APPLICATION
    _PROJECT_APPLICATION_CREATED(HttpStatus.CREATED, "PROJECT_APPLICATION201", "프로젝트 신청이 완료되었습니다."),
    _PROJECT_APPLICATION_DECIDED(HttpStatus.OK, "PROJECT_APPLICATION200", "프로젝트 신청이 처리되었습니다.");


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;


    SuccessCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
