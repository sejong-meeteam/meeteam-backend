package com.wardk.meeteam_backend.domain.file.service;

import java.util.regex.Pattern;

/** Only application-generated image paths; never accept arbitrary S3 object keys. */
final class S3ImageKeys {
    private static final Pattern ALLOWED = Pattern.compile(
            "^(?:images/(?:profiles/)?|profiles/)[A-Za-z0-9_-]+\\.(?i:jpg|jpeg|png|gif|webp)$");

    private S3ImageKeys() {}

    static boolean isAllowed(String key) {
        return key != null && ALLOWED.matcher(key).matches();
    }
}
