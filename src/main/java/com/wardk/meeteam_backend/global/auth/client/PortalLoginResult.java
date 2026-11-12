package com.wardk.meeteam_backend.global.auth.client;

import java.util.regex.Pattern;

/** Reads result assignments, not mentions inside an error-handling branch. Never executes scripts. */
final class PortalLoginResult {
    enum Status { OK, INVALID_CREDENTIALS, RESET_REQUIRED, ACCESS_DENIED, UNKNOWN, AMBIGUOUS }
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "\\bresult\\s*=(?!=)\\s*(['\"])([^'\"\\r\\n]{0,64})\\1");
    private PortalLoginResult() { }
    static Status parse(String html) {
        if (html == null) return Status.UNKNOWN;
        var matcher = ASSIGNMENT.matcher(html);
        String value = null;
        int count = 0;
        while (matcher.find()) { value = matcher.group(2); count++; }
        if (count > 1) return Status.AMBIGUOUS;
        // Some adapters return just the documented result word.
        if (count == 0) value = html.trim();
        return switch (value) {
            case "OK" -> Status.OK;
            case "erridpwd" -> Status.INVALID_CREDENTIALS;
            case "pwdNeedChg" -> Status.RESET_REQUIRED;
            case "noaccess" -> Status.ACCESS_DENIED;
            default -> Status.UNKNOWN;
        };
    }
}
