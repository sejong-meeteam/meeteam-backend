package com.wardk.meeteam_backend.global.auth.client;

/** Verified portal identity. Never include this record in application logs. */
public record SejongPortalProfile(String studentId, String name, String department) {
    public SejongPortalProfile {
        if (studentId == null || !studentId.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("Invalid portal student ID");
        }
        validateText(name, 100);
        validateText(department, 200);
    }

    private static void validateText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
                || value.codePoints().anyMatch(c -> Character.isISOControl(c)
                    || Character.getType(c) == Character.FORMAT)) {
            throw new IllegalArgumentException("Invalid portal profile field");
        }
    }

    @Override
    public String toString() {
        return "SejongPortalProfile[redacted]";
    }
}
