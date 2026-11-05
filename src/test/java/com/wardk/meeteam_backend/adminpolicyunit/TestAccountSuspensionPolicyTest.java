package com.wardk.meeteam_backend.adminpolicyunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.service.TestAccountService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestAccountSuspensionPolicyTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private TestAccountService service;
    private static final String PASSWORD = "unit-only-password-1234";

    @BeforeEach
    void setup() {
        when(encoder.encode(PASSWORD)).thenReturn("dummy-hash");
        service = new TestAccountService(members, encoder, jwt, PASSWORD);
    }

    @Test
    void startupNeverUnsuspendsOrResetsExistingReservedAccount() {
        Member member = Member.createForTest(TestAccountService.EMAIL, "Unit", "existing-hash");
        member.suspend(null, "policy", LocalDateTime.now());
        when(members.findByEmail(TestAccountService.EMAIL)).thenReturn(Optional.of(member));
        when(encoder.matches(PASSWORD, "existing-hash")).thenReturn(true);
        service.run(new DefaultApplicationArguments());
        assertThat(member.isSuspended()).isTrue();
        assertThat(member.getAuthVersion()).isEqualTo(1L);
        assertThat(member.getPassword()).isEqualTo("existing-hash");
        verify(members, never()).save(any());
        verifyNoInteractions(jwt);
    }

    @Test
    void startupRejectsDeletedReservedIdentityWithoutOverwriting() {
        Member member = Member.createForTest(TestAccountService.EMAIL, "Unit", "existing-hash");
        member.withdraw();
        when(members.findByEmail(TestAccountService.EMAIL)).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> service.run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
        verify(members, never()).save(any());
    }

    @Test
    void validPasswordDoesNotBypassSuspension() {
        Member member = Member.createForTest(TestAccountService.EMAIL, "Unit", "existing-hash");
        member.suspend(null, "policy", LocalDateTime.now());
        when(members.findByEmailAndDeletedAtIsNull(TestAccountService.EMAIL)).thenReturn(Optional.of(member));
        when(encoder.matches(PASSWORD, "existing-hash")).thenReturn(true);
        assertThatThrownBy(() -> service.login(TestAccountService.USERNAME, PASSWORD))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_SUSPENDED));
        assertThat(member.getLastLoginAt()).isNull();
        verifyNoInteractions(jwt);
    }

    @Test
    void successfulLoginRecordsTimestampWithoutClearingExpiredSuspensionHistory() {
        Member member = Member.createForTest(TestAccountService.EMAIL, "Unit", "existing-hash");
        member.suspend(1, "expired", LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(2));
        when(members.findByEmailAndDeletedAtIsNull(TestAccountService.EMAIL)).thenReturn(Optional.of(member));
        when(encoder.matches(PASSWORD, "existing-hash")).thenReturn(true);
        service.login(TestAccountService.USERNAME, PASSWORD);
        assertThat(member.getLastLoginAt()).isNotNull();
        assertThat(member.getAuthVersion()).isEqualTo(1L);
        assertThat(member.getSuspendReason()).isEqualTo("expired");
        verify(jwt).createAccessToken(member);
        verify(jwt).createRefreshToken(member);
    }
}
