package com.wardk.meeteam_backend.global.auth.service.dto;

import com.wardk.meeteam_backend.web.auth.dto.register.TechStackOrderRequest;

/**
 * 기술스택 선택 정보를 담는 Command 객체.
 * 기술스택 ID와 표시 순서(displayOrder)를 포함합니다.
 */
public record TechStackOrderCommand(
        Long id,
        Integer displayOrder
) {
    public static TechStackOrderCommand from(TechStackOrderRequest request) {
        return new TechStackOrderCommand(
                request.id(),
                request.displayOrder()
        );
    }
}