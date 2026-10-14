package com.wardk.meeteam_backend.web.notification;

public enum ApprovalResult {

    APPROVED("승인"),
    REJECTED("거절");


    private String message;

    ApprovalResult(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
