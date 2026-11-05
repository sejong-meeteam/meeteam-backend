package com.wardk.meeteam_backend.domain.github.account.oauth;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Stores only short-lived, single-use PKCE verifiers, never signed state. */
@Component
public class GithubOAuthStateStore {
    static final String PREFIX = "github:oauth:account:";
    static final Duration TTL = Duration.ofMinutes(10);
    private final StringRedisTemplate redis;

    public GithubOAuthStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void save(String nonce, String verifier) {
        requireToken(nonce);
        requireToken(verifier);
        try {
            // NX prevents even an astronomically unlikely nonce collision from replacing a verifier.
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + nonce, verifier, TTL))) {
                throw new IllegalStateException();
            }
        } catch (RuntimeException ignored) {
            throw new CustomException(ErrorCode.GITHUB_NOT_CONFIGURED);
        }
    }

    public String consume(String nonce) {
        requireToken(nonce);
        final String verifier;
        try {
            // Spring Data implements this as Redis GETDEL: concurrent callbacks cannot both win.
            verifier = redis.opsForValue().getAndDelete(PREFIX + nonce);
        } catch (RuntimeException ignored) {
            throw new CustomException(ErrorCode.GITHUB_NOT_CONFIGURED);
        }
        requireToken(verifier);
        return verifier;
    }

    static void requireToken(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
            throw new CustomException(ErrorCode.GITHUB_INVALID_STATE);
        }
    }
}
