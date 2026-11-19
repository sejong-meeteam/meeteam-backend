package com.wardk.meeteam_backend.domain.member.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Universities whose account domains count as school verification. Mirrors the frontend university registry. */
public enum University {
    SEJONG(List.of("sju.ac.kr", "sejong.ac.kr"));

    private final List<String> emailDomains;

    University(List<String> emailDomains) {
        this.emailDomains = emailDomains;
    }

    public static Optional<University> fromEmail(String email) {
        if (email == null) return Optional.empty();
        int at = email.lastIndexOf('@');
        if (at < 0) return Optional.empty();
        String domain = email.substring(at + 1).strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(university -> university.emailDomains.contains(domain)).findFirst();
    }
}
