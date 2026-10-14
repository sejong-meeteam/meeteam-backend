package com.wardk.meeteam_backend.domain.project.entity;

public enum Recruitment {
    RECRUITING, // 모집중
    CLOSED,     // 모집마감 (자동 - 전체 포지션 마감)
    SUSPENDED   // 모집중단 (리더 수동)
}
