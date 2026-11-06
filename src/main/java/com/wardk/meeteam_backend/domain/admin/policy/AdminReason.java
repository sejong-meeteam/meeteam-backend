package com.wardk.meeteam_backend.domain.admin.policy;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;

public final class AdminReason {
    private AdminReason() {}
    public static String validate(String value) {
        String text = value == null ? "" : value.replaceAll("(?U)^[\\s\\uFEFF]+|[\\s\\uFEFF]+$", "");
        if (text.length() < 5) throw new CustomException(ErrorCode.ADMIN_INVALID_REQUEST, "사유를 5자 이상 입력해 주세요.");
        if (text.length() > 200) throw new CustomException(ErrorCode.ADMIN_INVALID_REQUEST, "사유는 200자 이내로 입력해 주세요.");
        return text;
    }
}
