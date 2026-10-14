package com.wardk.meeteam_backend.global.constant;

public class PatternConstants {
    private PatternConstants() {
    }

    public static final String GITHUB_REPOSITORY_URL_PATTERN = "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?$";

    public static final String DISCORD_GG = "discord\\.gg/[A-Za-z0-9]+";
    public static final String DISCORD_INVITE = "discord\\.com/invite/[A-Za-z0-9]+";
    private static final String SLACK_JOIN = "join\\.slack\\.com/t/[A-Za-z0-9/_-]+";
    private static final String SLACK_WORKSPACE = "[A-Za-z0-9-]+\\.slack\\.com";
    private static final String KAKAO_OPEN_CHAT = "open\\.kakao\\.com/o/[A-Za-z0-9]+";

    // 통합 패턴
    public static final String COMMUNICATION_CHANNEL_URL_PATTERN =
            "^https://(" +
                    DISCORD_GG + "|" +
                    DISCORD_INVITE + "|" +
                    SLACK_JOIN + "|" +
                    SLACK_WORKSPACE + "|" +
                    KAKAO_OPEN_CHAT +
                    ")/?$";
}
