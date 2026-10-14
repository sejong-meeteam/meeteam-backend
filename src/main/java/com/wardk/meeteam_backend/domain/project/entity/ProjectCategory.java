package com.wardk.meeteam_backend.domain.project.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectCategory {
    CAPSTONE("캡스톤"),
    CREATIVE_SEMESTER("창의학기제"),
    CLUB("동아리"),
    ETC("기타");

    private final String displayName;
}