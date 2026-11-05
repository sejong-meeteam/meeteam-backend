package com.wardk.meeteam_backend.domain.github.account.oauth;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GithubOAuthStateService {
    static final String PURPOSE = "github-account-link";
    private static final int MAX_STATE_LENGTH = 4096;
    private final GithubAccountOAuthProperties properties;
    private final GithubOAuthStateStore store;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public GithubOAuthStateService(GithubAccountOAuthProperties properties, GithubOAuthStateStore store) {
        this(properties, store, Clock.systemUTC());
    }

    GithubOAuthStateService(GithubAccountOAuthProperties properties, GithubOAuthStateStore store, Clock clock) {
        this.properties = properties;
        this.store = store;
        this.clock = clock;
    }

    public Issued issue(long memberId, long authVersion, long linkVersion, String redirectPath) {
        properties.requireEnabled();
        SecretKey key = signingKey();
        String path;
        try {
            if (memberId <= 0 || authVersion < 0 || linkVersion < 0) {
                throw new IllegalArgumentException();
            }
            path = GithubOAuthRedirectPolicy.validatePath(redirectPath);
        } catch (RuntimeException ignored) {
            throw invalidState();
        }
        String nonce = randomToken();
        String verifier = randomToken();
        String state = Jwts.builder()
                .claim("purpose", PURPOSE)
                .claim("memberId", memberId)
                .claim("authVersion", authVersion)
                .claim("linkVersion", linkVersion)
                .claim("redirectPath", path)
                .claim("nonce", nonce)
                .expiration(Date.from(clock.instant().plus(GithubOAuthStateStore.TTL)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        String challenge;
        try {
            challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException ignored) {
            throw new CustomException(ErrorCode.GITHUB_NOT_CONFIGURED);
        }
        store.save(nonce, verifier);
        return new Issued(state, challenge);
    }

    public Validated consume(String state) {
        properties.requireEnabled();
        SecretKey key = signingKey();
        final long memberId;
        final long authVersion;
        final long linkVersion;
        final String path;
        final String nonce;
        try {
            if (state == null || state.isBlank() || state.length() > MAX_STATE_LENGTH) {
                throw new IllegalArgumentException();
            }
            Claims claims = Jwts.parser().verifyWith(key)
                    .sig().clear().add(Jwts.SIG.HS256).and()
                    .clock(() -> Date.from(clock.instant()))
                    .build().parseSignedClaims(state).getPayload();
            if (!PURPOSE.equals(claims.get("purpose"))) {
                throw new IllegalArgumentException();
            }
            Instant now = clock.instant();
            long expirySeconds = integerClaim(claims.get("exp"));
            Instant expiry = Instant.ofEpochSecond(expirySeconds);
            if (!expiry.isAfter(now) || expiry.isAfter(now.plus(GithubOAuthStateStore.TTL))) {
                throw new IllegalArgumentException();
            }
            memberId = integerClaim(claims.get("memberId"));
            authVersion = integerClaim(claims.get("authVersion"));
            linkVersion = integerClaim(claims.get("linkVersion"));
            if (memberId <= 0 || authVersion < 0 || linkVersion < 0) {
                throw new IllegalArgumentException();
            }
            Object rawPath = claims.get("redirectPath");
            if (!(rawPath instanceof String)) {
                throw new IllegalArgumentException();
            }
            path = GithubOAuthRedirectPolicy.validatePath((String) rawPath);
            Object rawNonce = claims.get("nonce");
            if (!(rawNonce instanceof String)) {
                throw new IllegalArgumentException();
            }
            nonce = (String) rawNonce;
            GithubOAuthStateStore.requireToken(nonce);
        } catch (RuntimeException ignored) {
            // No untrusted token, JWT exception message, or cause escapes to logs/clients.
            throw invalidState();
        }
        // Only authenticated, bounded claims may trigger a destructive lookup.
        String verifier = store.consume(nonce);
        GithubOAuthStateStore.requireToken(verifier);
        return new Validated(memberId, authVersion, linkVersion, path, verifier);
    }

    private SecretKey signingKey() {
        try {
            byte[] bytes = Base64.getDecoder().decode(properties.getStateSecret());
            if (bytes.length < 32) {
                throw new IllegalArgumentException();
            }
            return new SecretKeySpec(bytes, "HmacSHA256");
        } catch (RuntimeException ignored) {
            throw new CustomException(ErrorCode.GITHUB_NOT_CONFIGURED);
        }
    }

    private static long integerClaim(Object value) {
        // Reject strings, floating point values, and overflowing BigInteger values.
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException();
        }
        return ((Number) value).longValue();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static CustomException invalidState() {
        return new CustomException(ErrorCode.GITHUB_INVALID_STATE);
    }

    public record Issued(String state, String codeChallenge) {
        @Override
        public String toString() {
            return "Issued[state=[REDACTED], codeChallenge=[REDACTED]]";
        }
    }

    public record Validated(long memberId, long authVersion, long linkVersion, String redirectPath, String codeVerifier) {
        @Override
        public String toString() {
            return "Validated[codeVerifier=[REDACTED]]";
        }
    }
}
