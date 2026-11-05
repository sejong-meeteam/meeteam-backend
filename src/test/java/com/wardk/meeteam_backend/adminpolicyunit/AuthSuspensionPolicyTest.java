package com.wardk.meeteam_backend.adminpolicyunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.client.SejongPortalClient;
import com.wardk.meeteam_backend.global.auth.service.AuthService;
import com.wardk.meeteam_backend.global.auth.service.TestAccountService;
import com.wardk.meeteam_backend.global.auth.service.dto.SejongLoginCommand;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.exception.handler.GlobalExceptionHandler;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthSuspensionPolicyTest {
    @Mock MemberRepository members;
    @Mock JwtUtil jwt;
    @Mock SejongPortalClient portal;
    @Mock ObjectProvider<TestAccountService> testAccountService;
    @InjectMocks AuthService auth;
    private Member member;

    @BeforeEach
    void setup() {
        member = Member.createForTest("unit@example.invalid", "Unit");
        ReflectionTestUtils.setField(member, "id", 12L);
    }

    @Test
    void suspendedLoginReturns403WithUntilAndReasonAndIssuesNoTokens() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        member.suspend(7, "운영 정책 위반", now);
        when(members.findByStudentIdAndDeletedAtIsNull("12345678")).thenReturn(Optional.of(member));
        CustomException failure = catchThrowableOfType(() -> auth.sejongLogin(
                new SejongLoginCommand("12345678", "unit-password")), CustomException.class);
        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.MEMBER_SUSPENDED);
        assertThat(failure.getMessage()).contains("운영 정책 위반", member.getSuspendedUntil().toString());
        assertThat(new GlobalExceptionHandler().handleCustomException(failure).getStatusCode().value()).isEqualTo(403);
        assertThat(member.getLastLoginAt()).isNull();
        verifyNoInteractions(jwt);
    }

    @Test
    void expiredSuspensionAllowsLoginAndRecordsKoreanLocalTime() {
        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        member.suspend(1, "expired", before.minusDays(2));
        when(members.findByStudentIdAndDeletedAtIsNull("12345678")).thenReturn(Optional.of(member));
        auth.sejongLogin(new SejongLoginCommand("12345678", "unit-password"));
        assertThat(member.getLastLoginAt()).isBetween(before, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        verify(jwt).createAccessToken(member);
        verify(jwt).createRefreshToken(member);
    }

    private MockHttpServletRequest refreshRequest() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtUtil.REFRESH_COOKIE_NAME, "refresh"));
        when(jwt.getCategory("refresh")).thenReturn(JwtUtil.REFRESH_CATEGORY);
        when(jwt.getUsername("refresh")).thenReturn(member.getEmail());
        when(members.findByEmailAndDeletedAtIsNull(member.getEmail())).thenReturn(Optional.of(member));
        return request;
    }

    @Test
    void suspendedMemberCannotRefresh() {
        member.suspend(null, "policy", LocalDateTime.now());
        assertThatThrownBy(() -> auth.refreshTokens(refreshRequest())).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        verify(jwt, never()).createAccessToken(any());
    }

    @Test
    void oldRefreshRemainsRevokedAfterUnsuspension() {
        member.suspend(null, "policy", LocalDateTime.now());
        member.unsuspend();
        when(jwt.getMemberId("refresh")).thenReturn(12L);
        assertThatThrownBy(() -> auth.refreshTokens(refreshRequest())).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        verify(jwt, never()).createAccessToken(any());
    }

    @Test
    void reusedEmailDoesNotAllowRefreshForNewMemberId() {
        when(jwt.getMemberId("refresh")).thenReturn(99L);
        assertThatThrownBy(() -> auth.refreshTokens(refreshRequest())).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        verify(jwt, never()).createAccessToken(any());
    }

    @Test
    void currentIdentityAndVersionCanRefreshAfterSuspensionExpires() {
        member.suspend(1, "expired", LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(2));
        when(jwt.getMemberId("refresh")).thenReturn(12L);
        when(jwt.getAuthVersion("refresh")).thenReturn(member.getAuthVersion());
        auth.refreshTokens(refreshRequest());
        verify(jwt).createAccessToken(member);
        verify(jwt).createRefreshToken(member);
        assertThat(member.getLastLoginAt()).isNull();
    }
}
