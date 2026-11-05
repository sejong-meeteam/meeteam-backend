package com.wardk.meeteam_backend.domain.github.account.oauth;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GithubOAuthStateServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final SecretKey KEY = new SecretKeySpec(SECRET, "HmacSHA256");
    private static final String NONCE = "a".repeat(43);
    private static final String VERIFIER = "b".repeat(43);
    private GithubAccountOAuthProperties properties;
    private GithubOAuthStateStore store;
    private GithubOAuthStateService service;

    @BeforeEach
    void setup() {
        properties = mock(GithubAccountOAuthProperties.class);
        when(properties.getStateSecret()).thenReturn(Base64.getEncoder().encodeToString(SECRET));
        store = mock(GithubOAuthStateStore.class);
        service = new GithubOAuthStateService(properties, store, CLOCK);
    }

    @Test
    void issuesPurposeBoundTenMinuteStateAndStoresOnlyVerifier() throws Exception {
        GithubOAuthStateService.Issued issued = service.issue(7, 3, 2, "/settings");
        ArgumentCaptor<String> nonce = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> verifier = ArgumentCaptor.forClass(String.class);
        verify(store).save(nonce.capture(), verifier.capture());
        assertThat(nonce.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(verifier.getValue()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(nonce.getValue());
        assertThat(Base64.getUrlDecoder().decode(nonce.getValue())).hasSize(32);
        assertThat(Base64.getUrlDecoder().decode(verifier.getValue())).hasSize(32);
        var jwt = Jwts.parser().verifyWith(KEY).clock(() -> Date.from(NOW)).build().parseSignedClaims(issued.state());
        Claims claims = jwt.getPayload();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.get("purpose")).isEqualTo("github-account-link");
        assertThat(((Number) claims.get("memberId")).longValue()).isEqualTo(7);
        assertThat(((Number) claims.get("authVersion")).longValue()).isEqualTo(3);
        assertThat(((Number) claims.get("linkVersion")).longValue()).isEqualTo(2);
        assertThat(claims.get("nonce")).isEqualTo(nonce.getValue());
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(600));
        assertThat(claims).doesNotContainKeys("codeVerifier", "verifier");
        assertThat(issued.codeChallenge()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getValue().getBytes(StandardCharsets.US_ASCII))));
        assertThat(issued.toString()).doesNotContain(issued.state(), issued.codeChallenge());
    }

    @Test
    void consumesOnlyAfterValidationAndRejectsReplay() {
        when(store.consume(NONCE)).thenReturn(VERIFIER).thenThrow(new CustomException(ErrorCode.GITHUB_INVALID_STATE));
        String state = token(validClaims());
        var result = service.consume(state);
        assertThat(result.memberId()).isEqualTo(7);
        assertThat(result.authVersion()).isEqualTo(3);
        assertThat(result.linkVersion()).isEqualTo(2);
        assertThat(result.redirectPath()).isEqualTo("/settings");
        assertThat(result.codeVerifier()).isEqualTo(VERIFIER);
        assertThat(result.toString()).doesNotContain(VERIFIER);
        assertInvalid(state);
        verify(store, times(2)).consume(NONCE);
    }

    static Object[][] badClaims() {
        return new Object[][] {
                {"purpose", null}, {"purpose", "access"}, {"purpose", 4},
                {"memberId", null}, {"memberId", 0}, {"memberId", -1}, {"memberId", "7"}, {"memberId", 7.5},
                {"linkVersion", null}, {"linkVersion", -1}, {"linkVersion", "2"}, {"linkVersion", 2.5},
                {"authVersion", null}, {"authVersion", -1}, {"authVersion", "3"}, {"authVersion", 3.5},
                {"nonce", null}, {"nonce", "a".repeat(42)}, {"nonce", "a".repeat(44)}, {"nonce", "+".repeat(43)}, {"nonce", 42},
                {"redirectPath", null}, {"redirectPath", 42}, {"redirectPath", "https://evil.example"}, {"redirectPath", "//evil.example"},
                {"exp", null}, {"exp", NOW.minusSeconds(1).getEpochSecond()}, {"exp", NOW.getEpochSecond()},
                {"exp", NOW.plusSeconds(601).getEpochSecond()}
        };
    }

    @ParameterizedTest
    @MethodSource("badClaims")
    void rejectsInvalidClaimsWithoutDestructiveLookup(String name, Object value) {
        Map<String, Object> claims = validClaims();
        if (value == null) claims.remove(name); else claims.put(name, value);
        assertInvalid(token(claims));
        verifyNoInteractions(store);
    }

    @Test
    void rejectsWrongSignatureAndHs512WithoutLookup() {
        SecretKey wrong = new SecretKeySpec(new byte[64], "HmacSHA256");
        assertInvalid(Jwts.builder().claims(validClaims()).signWith(wrong, Jwts.SIG.HS256).compact());
        assertInvalid(Jwts.builder().claims(validClaims()).signWith(KEY, Jwts.SIG.HS512).compact());
        verifyNoInteractions(store);
    }

    @Test
    void rejectsMissingMalformedAndOversizedStateWithoutLookup() {
        for (String state : new String[] {null, "", " ", "not-a-jwt", "x".repeat(4097)}) assertInvalid(state);
        verifyNoInteractions(store);
    }

    @Test
    void acceptsIntegerBoundsAndExactlyTenMinuteExpiry() {
        Map<String, Object> claims = validClaims();
        claims.put("memberId", Long.MAX_VALUE);
        claims.put("authVersion", Long.MAX_VALUE);
        claims.put("exp", NOW.plusSeconds(600).getEpochSecond());
        when(store.consume(NONCE)).thenReturn(VERIFIER);
        var result = service.consume(token(claims));
        assertThat(result.memberId()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.authVersion()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsInvalidVerifierEvenIfStoreImplementationReturnsIt() {
        when(store.consume(NONCE)).thenReturn("bad");
        assertInvalid(token(validClaims()));
    }

    @Test
    void redisFailureFailsClosedAndDoesNotExposeCause() {
        when(store.consume(NONCE)).thenThrow(new CustomException(ErrorCode.GITHUB_NOT_CONFIGURED));
        assertThatThrownBy(() -> service.consume(token(validClaims())))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GITHUB_NOT_CONFIGURED);
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    void rejectsInvalidIssuanceArgumentsWithoutStorage() {
        assertThatThrownBy(() -> service.issue(0, 0, 0, "/settings")).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.issue(7, -1, 0, "/settings")).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.issue(7, 0, 0, "//evil.example")).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.issue(7, 0, -1, "/settings")).isInstanceOf(CustomException.class);
        verifyNoInteractions(store);
    }

    @Test
    void invalidSecretFailsClosed() {
        for (String secret : new String[] {null, "not base64!", Base64.getEncoder().encodeToString(new byte[31])}) {
            when(properties.getStateSecret()).thenReturn(secret);
            assertThatThrownBy(() -> service.issue(7, 0, 0, "/settings"))
                    .isInstanceOfSatisfying(CustomException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GITHUB_NOT_CONFIGURED);
                        assertThat(e.getCause()).isNull();
                    });
        }
        verifyNoInteractions(store);
    }

    @Test
    void disabledIntegrationCannotIssueOrConsume() {
        doThrow(new CustomException(ErrorCode.GITHUB_NOT_CONFIGURED)).when(properties).requireEnabled();
        assertThatThrownBy(() -> service.issue(7, 0, 0, "/settings")).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.consume(token(validClaims()))).isInstanceOf(CustomException.class);
        verifyNoInteractions(store);
    }

    private void assertInvalid(String state) {
        assertThatThrownBy(() -> service.consume(state)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GITHUB_INVALID_STATE);
            assertThat(e.getCause()).isNull();
            if (state != null && !state.isBlank()) assertThat(e.getMessage()).doesNotContain(state);
        });
    }

    private static Map<String, Object> validClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("purpose", "github-account-link");
        claims.put("memberId", 7L);
        claims.put("authVersion", 3L);
        claims.put("linkVersion", 2L);
        claims.put("nonce", NONCE);
        claims.put("redirectPath", "/settings");
        claims.put("exp", NOW.plusSeconds(300).getEpochSecond());
        return claims;
    }

    private static String token(Map<String, Object> claims) {
        return Jwts.builder().claims(claims).signWith(KEY, Jwts.SIG.HS256).compact();
    }
}
