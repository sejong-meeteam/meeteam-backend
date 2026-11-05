package com.wardk.meeteam_backend.domain.github.account.oauth;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GithubOAuthStateStoreTest {
    private static final String NONCE = "a".repeat(43);
    private static final String VERIFIER = "b".repeat(43);
    private static final String KEY = "github:oauth:account:" + NONCE;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private GithubOAuthStateStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        store = new GithubOAuthStateStore(redis);
    }

    @Test
    void storesVerifierWithTenMinuteTtlAndCollisionProtection() {
        when(values.setIfAbsent(KEY, VERIFIER, Duration.ofMinutes(10))).thenReturn(true);
        store.save(NONCE, VERIFIER);
        verify(values).setIfAbsent(KEY, VERIFIER, Duration.ofMinutes(10));
        verifyNoMoreInteractions(values);
    }

    @Test
    void consumesUsingSingleAtomicGetDeleteAndRejectsSecondUse() {
        when(values.getAndDelete(KEY)).thenReturn(VERIFIER).thenReturn(null);
        assertThat(store.consume(NONCE)).isEqualTo(VERIFIER);
        assertError(() -> store.consume(NONCE), ErrorCode.GITHUB_INVALID_STATE);
        verify(values, times(2)).getAndDelete(KEY);
        verifyNoMoreInteractions(values);
    }

    @Test
    void missingOrCorruptVerifierIsInvalid() {
        for (String verifier : new String[] {null, "", "a".repeat(42), "+".repeat(43)}) {
            when(values.getAndDelete(KEY)).thenReturn(verifier);
            assertError(() -> store.consume(NONCE), ErrorCode.GITHUB_INVALID_STATE);
        }
    }

    @Test
    void collisionsAndIndeterminateSaveFailClosed() {
        for (Boolean accepted : new Boolean[] {false, null}) {
            when(values.setIfAbsent(KEY, VERIFIER, Duration.ofMinutes(10))).thenReturn(accepted);
            assertError(() -> store.save(NONCE, VERIFIER), ErrorCode.GITHUB_NOT_CONFIGURED);
        }
    }

    @Test
    void redisReadAndWriteFailuresHaveFixedErrorsWithoutSecretCause() {
        when(values.getAndDelete(KEY)).thenThrow(new IllegalStateException(VERIFIER));
        when(values.setIfAbsent(KEY, VERIFIER, Duration.ofMinutes(10))).thenThrow(new IllegalStateException(VERIFIER));
        assertError(() -> store.consume(NONCE), ErrorCode.GITHUB_NOT_CONFIGURED);
        assertError(() -> store.save(NONCE, VERIFIER), ErrorCode.GITHUB_NOT_CONFIGURED);
    }

    @Test
    void malformedNonceOrVerifierNeverReachesRedis() {
        for (String invalid : new String[] {null, "", "a".repeat(42), "a".repeat(44), "+".repeat(43), "/".repeat(43)}) {
            assertError(() -> store.consume(invalid), ErrorCode.GITHUB_INVALID_STATE);
            assertError(() -> store.save(invalid, VERIFIER), ErrorCode.GITHUB_INVALID_STATE);
            assertError(() -> store.save(NONCE, invalid), ErrorCode.GITHUB_INVALID_STATE);
        }
        verifyNoInteractions(values);
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(code);
            assertThat(e.getCause()).isNull();
            assertThat(e.getMessage()).doesNotContain(VERIFIER, NONCE);
        });
    }
}
