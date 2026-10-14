package com.wardk.meeteam_backend.domain.job.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 직무(JobPosition) 코드 Enum.
 * 각 직무가 어느 직군(JobField)에 속하는지 함께 정의합니다.
 */
@Getter
@RequiredArgsConstructor
public enum JobPositionCode {

    // 기획 (PLANNING)
    PRODUCT_MANAGER(JobFieldCode.PLANNING, "PM 프로덕트 매니저", "Product Manager"),
    PRODUCT_OWNER(JobFieldCode.PLANNING, "PO 프로덕트 오너", "Product Owner"),
    SERVICE_PLANNER(JobFieldCode.PLANNING, "서비스 기획", "Service Planner"),

    // 디자인 (DESIGN)
    UI_UX_DESIGNER(JobFieldCode.DESIGN, "UI/UX 디자이너", "Product Designer"),
    MOTION_DESIGNER(JobFieldCode.DESIGN, "모션 디자이너", "Motion Designer"),
    BX_BRAND_DESIGNER(JobFieldCode.DESIGN, "BX 브랜드 디자이너", "Brand Designer"),

    // 프론트 (FRONTEND)
    WEB_FRONTEND(JobFieldCode.FRONTEND, "웹 프론트엔드", "Frontend Dev"),
    IOS(JobFieldCode.FRONTEND, "iOS", "iOS Dev"),
    ANDROID(JobFieldCode.FRONTEND, "Android", "Android Dev"),
    CROSS_PLATFORM(JobFieldCode.FRONTEND, "크로스 플랫폼", "Cross Platform Dev"),

    // 백엔드 (BACKEND)
    JAVA_SPRING(JobFieldCode.BACKEND, "Java/Spring", "Backend Dev"),
    KOTLIN_SPRING(JobFieldCode.BACKEND, "Kotlin/Spring", "Backend Dev"),
    NODE_NESTJS(JobFieldCode.BACKEND, "Node.js/NestJS", "Backend Dev"),
    PYTHON_BACKEND(JobFieldCode.BACKEND, "Python Backend", "Backend Dev"),

    // AI
    MACHINE_LEARNING(JobFieldCode.AI, "머신 러닝", "ML Engineer"),
    DEEP_LEARNING(JobFieldCode.AI, "딥러닝", "DL Engineer"),
    LLM(JobFieldCode.AI, "LLM", "LLM Engineer"),
    MLOPS(JobFieldCode.AI, "MLOps", "MLOps Engineer"),

    // 인프라/운영 (INFRA_OPERATION)
    DEVOPS_ARCHITECT(JobFieldCode.INFRA_OPERATION, "DevOps 엔지니어/아키텍처", "DevOps Engineer"),
    QA(JobFieldCode.INFRA_OPERATION, "QA", "QA Engineer"),
    CLOUD_ENGINEER(JobFieldCode.INFRA_OPERATION, "Cloud 엔지니어", "Cloud Engineer");

    private final JobFieldCode jobFieldCode;
    private final String displayName;
    private final String englishName;
}
