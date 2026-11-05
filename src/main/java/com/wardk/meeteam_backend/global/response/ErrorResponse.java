package com.wardk.meeteam_backend.global.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ErrorResponse {

    private String code;
    private String message;

    // Keep the failure envelope compatible with the frontend contract.
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public Object getResult() {
        return null;
    }

    public static ErrorResponse getResponse(String code, String message) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .build();
    }
}
