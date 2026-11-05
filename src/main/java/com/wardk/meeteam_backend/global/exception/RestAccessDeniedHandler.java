package com.wardk.meeteam_backend.global.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean adminRequest = path.equals("/api/v1/admin") || path.startsWith("/api/v1/admin/");
        ErrorCode ec = adminRequest ? ErrorCode.ADMIN_FORBIDDEN : ErrorCode.FORBIDDEN;
        ErrorResponse body = ErrorResponse.getResponse(ec.getCode(), ec.getMessage());

        response.setStatus(ec.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}