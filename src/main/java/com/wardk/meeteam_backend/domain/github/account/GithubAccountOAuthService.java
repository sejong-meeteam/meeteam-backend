package com.wardk.meeteam_backend.domain.github.account;

import com.wardk.meeteam_backend.domain.github.account.oauth.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.Objects;

/** GitHub network calls deliberately run outside the database mutation transaction. */
@Service
@RequiredArgsConstructor
public class GithubAccountOAuthService {
    private final GithubAccountService accounts;
    private final GithubAccountOAuthProperties properties;
    private final GithubOAuthStateService states;
    private final GithubAccountOAuthClient client;

    public String authorize(long memberId, String redirectPath) {
        properties.requireEnabled();
        String safePath = GithubOAuthRedirectPolicy.validatePath(redirectPath);
        var member = accounts.context(memberId);
        var issued = states.issue(memberId, member.authVersion(), member.linkVersion(), safePath);
        return client.authorizeUrl(issued.state(), issued.codeChallenge());
    }
    public URI callback(String code, String state, String error, Long currentMemberId) {
        properties.requireEnabled();
        String redirectPath = "/settings";
        try {
            var verified = states.consume(state);
            redirectPath = verified.redirectPath();
            // State alone cannot prove the callback browser owns the initiating MeeTeam session.
            if (!Objects.equals(currentMemberId, verified.memberId()) || error != null || code == null || code.isBlank() || code.length() > 1024)
                return GithubOAuthRedirectPolicy.resultUri(properties.getFrontOrigin(), redirectPath, false);
            var identity = client.identify(code, verified.codeVerifier());
            accounts.connect(verified, identity);
            return GithubOAuthRedirectPolicy.resultUri(properties.getFrontOrigin(), redirectPath, true);
        } catch (RuntimeException ignored) {
            // Upstream/Redis/constraint details may contain credentials. Do not log or reflect them.
            return GithubOAuthRedirectPolicy.resultUri(properties.getFrontOrigin(), redirectPath, false);
        }
    }
}
