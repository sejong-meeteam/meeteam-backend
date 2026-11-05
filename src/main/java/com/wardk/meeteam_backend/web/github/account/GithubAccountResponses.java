package com.wardk.meeteam_backend.web.github.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wardk.meeteam_backend.domain.github.account.MemberGithubAccount;
import com.wardk.meeteam_backend.global.response.SuccessCode;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class GithubAccountResponses {
    private GithubAccountResponses() {}
    public record Envelope<T>(String code, String message, @JsonInclude(JsonInclude.Include.ALWAYS) T result) {
        public static <T> Envelope<T> ok(T result) {
            return new Envelope<>(SuccessCode._OK.getCode(), SuccessCode._OK.getMessage(), result);
        }
    }
    public record AuthorizeUrl(String authorizeUrl) {}
    public record Account(String login, String avatarUrl, String profileUrl, OffsetDateTime connectedAt) {
        public static Account from(MemberGithubAccount account) {
            return new Account(account.getLogin(), account.getAvatarUrl() == null ? "" : account.getAvatarUrl(), "https://github.com/" + account.getLogin(),
                    account.getConnectedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
        }
    }
}
