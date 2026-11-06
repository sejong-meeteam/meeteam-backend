package com.wardk.meeteam_backend.web.admin.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.wardk.meeteam_backend.domain.project.entity.ProjectVisibility;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;

/** Strict JSON types match zod: no silent numeric-string/float-to-int coercion. */
public final class AdminMutationRequests {
    private AdminMutationRequests() {}
    public record Suspension(Integer days, String reason) {}
    public record Role(com.wardk.meeteam_backend.domain.member.entity.UserRole role, String reason) {}
    public record Visibility(ProjectVisibility visibility, String reason) {}
    private static void object(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("요청 내용을 확인해 주세요.");
    }
    public static String reason(JsonNode body) {
        object(body);
        JsonNode value = body.get("reason");
        if (value == null || !value.isTextual()) throw invalid("사유를 5자 이상 입력해 주세요.");
        return com.wardk.meeteam_backend.domain.admin.policy.AdminReason.validate(value.textValue());
    }
    public static Suspension suspension(JsonNode body) {
        object(body);
        JsonNode days = body.get("days");
        if (days == null || (!days.isNull() && (!days.isIntegralNumber() || !days.canConvertToInt()
                || days.intValue() < 1 || days.intValue() > 365))) {
            throw invalid("정지 기간은 1일 이상 365일 이하의 정수 또는 null이어야 합니다.");
        }
        return new Suspension(days.isNull() ? null : days.intValue(), reason(body));
    }
    public static Visibility visibility(JsonNode body) {
        object(body);
        JsonNode value = body.get("visibility");
        if (value == null || !value.isTextual()) throw invalid("노출 상태를 확인해 주세요.");
        ProjectVisibility visibility;
        try { visibility = ProjectVisibility.valueOf(value.textValue()); }
        catch (IllegalArgumentException e) { throw invalid("노출 상태를 확인해 주세요."); }
        return new Visibility(visibility, reason(body));
    }
    public static Role role(JsonNode body) {
        object(body);
        JsonNode value = body.get("role");
        if (value == null || !value.isTextual()) throw invalid("권한을 확인해 주세요.");
        com.wardk.meeteam_backend.domain.member.entity.UserRole role;
        try { role = com.wardk.meeteam_backend.domain.member.entity.UserRole.valueOf(value.textValue()); }
        catch (IllegalArgumentException exception) { throw invalid("권한을 확인해 주세요."); }
        return new Role(role, reason(body));
    }
    private static CustomException invalid(String message) {
        return new CustomException(ErrorCode.ADMIN_INVALID_REQUEST, message);
    }
}
