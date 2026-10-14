package com.wardk.meeteam_backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"code", "message", "result"})
public class SuccessResponse<T> {
    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T result;

    // 성공한 경우 응답 생성
    // enum 상수들은 static 이므로 "클래스명." 으로 접근이 가능하다.
    public static <T> SuccessResponse<T> onSuccess(T result){
        return new SuccessResponse<>(SuccessCode._OK.getCode() , SuccessCode._OK.getMessage(), result);
    }

    public static <T> SuccessResponse<T> of(SuccessCode code, T result){
        return new SuccessResponse<>(code.getCode() , code.getMessage(), result);
    }
}