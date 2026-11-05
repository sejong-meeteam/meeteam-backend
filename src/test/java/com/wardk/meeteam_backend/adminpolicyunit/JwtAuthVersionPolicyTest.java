package com.wardk.meeteam_backend.adminpolicyunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.global.auth.service.CustomUserDetailsService;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtAuthVersionPolicyTest {
    private static final byte[] KEY = "unit-only-signing-key-not-production-0123456789".getBytes(StandardCharsets.UTF_8);
    private JwtUtil jwt;

    @BeforeEach
    void setup() {
        jwt = new JwtUtil(mock(CustomUserDetailsService.class));
        ReflectionTestUtils.setField(jwt, "secretKey", Base64.getEncoder().encodeToString(KEY));
        ReflectionTestUtils.setField(jwt, "accessTokenExpTime", 60000L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpTime", 120000L);
    }

    @Test
    void legacyTokensDefaultToZero() {
        String legacy = Jwts.builder().subject("unit@example.invalid")
                .signWith(Keys.hmacShaKeyFor(KEY)).compact();
        assertThat(jwt.getAuthVersion(legacy)).isZero();
    }

    @Test
    void bothTokenTypesCarryCurrentVersionAndImmutableIdentity() {
        Member member = Member.createForTest("unit@example.invalid", "Unit");
        ReflectionTestUtils.setField(member, "id", 31L);
        String oldAccess = jwt.createAccessToken(member);
        String oldRefresh = jwt.createRefreshToken(member);
        member.suspend(null, "policy", LocalDateTime.now());
        member.unsuspend();
        assertThat(jwt.getAuthVersion(oldAccess)).isZero();
        assertThat(jwt.getAuthVersion(oldRefresh)).isZero();
        for (String token : new String[]{jwt.createAccessToken(member), jwt.createRefreshToken(member)}) {
            assertThat(jwt.getAuthVersion(token)).isEqualTo(2L);
            assertThat(jwt.getMemberId(token)).isEqualTo(31L);
            assertThat(jwt.getUsername(token)).isEqualTo(member.getEmail());
        }
    }
}
