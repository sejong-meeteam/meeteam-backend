package com.wardk.meeteam_backend.adminloginunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.UserRole;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.service.AdminAccountService;
import com.wardk.meeteam_backend.global.auth.service.AuthService;
import com.wardk.meeteam_backend.global.auth.service.CustomUserDetailsService;
import com.wardk.meeteam_backend.global.auth.service.TestAccountService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import com.wardk.meeteam_backend.web.auth.dto.register.RegisterDescriptionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Isolated policy checks with synthetic identities and mocked tokens; no application or infrastructure starts. */
@ExtendWith(MockitoExtension.class)
class AdminAccountLifecyclePolicyTest {
    private static final long MEMBER_ID = 731L;
    private static final String REFRESH = "synthetic-lifecycle-refresh";
    @Mock private MemberRepository members;
    @Mock private JwtUtil jwt;
    @InjectMocks private AuthService auth;
    private CustomUserDetailsService details;

    @BeforeEach
    void setup() {
        details = new CustomUserDetailsService(members);
        // No admin service bean represents the explicitly disabled/default-disabled application state.
        ReflectionTestUtils.setField(auth, "adminAccountService", null);
    }

    private Member operator() {
        return withId(Member.createForLocalAdmin(AdminAccountService.EMAIL, "synthetic-unused-hash"));
    }

    private Member ordinaryTest() {
        return withId(Member.createForTest(TestAccountService.EMAIL, "Synthetic test member"));
    }

    private Member student() {
        Member member = withId(Member.createForTest("21000073@sju.ac.kr", "Synthetic student"));
        ReflectionTestUtils.setField(member, "provider", "sejong");
        ReflectionTestUtils.setField(member, "studentId", "21000073");
        return member;
    }

    private Member withId(Member member) {
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    private void stored(Member member) {
        when(members.findByEmailAndDeletedAtIsNull(member.getEmail())).thenReturn(Optional.of(member));
    }

    private void enableOperator() {
        ReflectionTestUtils.setField(details, "adminAccountEnabled", true);
        ReflectionTestUtils.setField(auth, "adminAccountService", mock(AdminAccountService.class));
    }

    private MockHttpServletRequest refreshRequest(Member member) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtUtil.REFRESH_COOKIE_NAME, REFRESH));
        when(jwt.isExpired(REFRESH)).thenReturn(false);
        when(jwt.getCategory(REFRESH)).thenReturn(JwtUtil.REFRESH_CATEGORY);
        when(jwt.getUsername(REFRESH)).thenReturn(member.getEmail());
        stored(member);
        return request;
    }

    private void matchingTokenIdentity(Member member) {
        when(jwt.getMemberId(REFRESH)).thenReturn(member.getId());
        when(jwt.getAuthVersion(REFRESH)).thenReturn(member.getAuthVersion());
    }

    private void verifyNoTokenIssuance() {
        verify(jwt, never()).createAccessToken(any(Member.class));
        verify(jwt, never()).createRefreshToken(any(Member.class));
    }

    @Test
    void localAdministratorCannotLoadWhenFeatureDefaultsToDisabled() {
        Member member = operator();
        stored(member);
        assertThrows(UsernameNotFoundException.class, () -> details.loadUserByUsername(member.getEmail()));
    }

    @Test
    void localAdministratorCannotLoadWhenFeatureExplicitlyDisabled() {
        ReflectionTestUtils.setField(details, "adminAccountEnabled", false);
        Member member = operator();
        stored(member);
        assertThrows(UsernameNotFoundException.class, () -> details.loadUserByUsername(member.getEmail()));
    }

    @Test
    void enabledLocalAdministratorLoadsWithExistingAdminAuthority() {
        enableOperator();
        Member member = operator();
        stored(member);
        var loaded = details.loadUserByUsername(member.getEmail());
        assertSame(member, loaded.getMember());
        assertTrue(loaded.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    void ordinaryTestAndStudentAccountsStillLoadWhenOperatorFeatureIsDisabled() {
        for (Member member : List.of(ordinaryTest(), student())) {
            stored(member);
            assertSame(member, details.loadUserByUsername(member.getEmail()).getMember());
        }
    }

    @Test
    void ordinaryPortalAdministratorIsNotDisabledByLocalOperatorSwitch() {
        Member member = student();
        member.changeRole(UserRole.ADMIN);
        stored(member);
        assertSame(member, details.loadUserByUsername(member.getEmail()).getMember());
    }

    @Test
    void enablingOperatorDoesNotBypassExistingSuspensionCheck() {
        enableOperator();
        Member member = operator();
        member.suspend(null, "Synthetic policy suspension", LocalDateTime.now());
        stored(member);
        assertThrows(DisabledException.class, () -> details.loadUserByUsername(member.getEmail()));
    }

    @Test
    void disabledLocalAdministratorRefreshIsInvalidAndIssuesNoTokens() {
        Member member = operator();
        MockHttpServletRequest request = refreshRequest(member);
        // Identity parsing may precede or follow the feature gate; either order must reject.
        lenient().when(jwt.getMemberId(REFRESH)).thenReturn(member.getId());
        lenient().when(jwt.getAuthVersion(REFRESH)).thenReturn(member.getAuthVersion());
        CustomException failure = assertThrows(CustomException.class, () -> auth.refreshTokens(request));
        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, failure.getErrorCode());
        verifyNoTokenIssuance();
        assertNull(member.getLastLoginAt());
    }

    @Test
    void enabledLocalAdministratorRefreshUsesExistingTokenFactory() {
        enableOperator();
        Member member = operator();
        MockHttpServletRequest request = refreshRequest(member);
        matchingTokenIdentity(member);
        when(jwt.createAccessToken(member)).thenReturn("synthetic-new-access");
        when(jwt.createRefreshToken(member)).thenReturn("synthetic-new-refresh");
        var result = auth.refreshTokens(request);
        assertEquals("synthetic-new-access", result.getAccessToken());
        assertEquals("synthetic-new-refresh", result.getRefreshToken());
        verify(jwt).createAccessToken(member);
        verify(jwt).createRefreshToken(member);
    }

    @Test
    void ordinaryTestAndStudentRefreshRemainAvailableWhenOperatorFeatureIsDisabled() {
        for (Member member : List.of(ordinaryTest(), student())) {
            MockHttpServletRequest request = refreshRequest(member);
            matchingTokenIdentity(member);
            auth.refreshTokens(request);
            verify(jwt).createAccessToken(member);
            verify(jwt).createRefreshToken(member);
        }
    }

    @Test
    void enabledOperatorRefreshStillRejectsMismatchedMemberId() {
        enableOperator();
        Member member = operator();
        MockHttpServletRequest request = refreshRequest(member);
        when(jwt.getMemberId(REFRESH)).thenReturn(MEMBER_ID + 1);
        CustomException failure = assertThrows(CustomException.class, () -> auth.refreshTokens(request));
        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, failure.getErrorCode());
        verifyNoTokenIssuance();
    }

    @Test
    void enabledOperatorRefreshStillRejectsStaleAuthVersion() {
        enableOperator();
        Member member = operator();
        MockHttpServletRequest request = refreshRequest(member);
        when(jwt.getMemberId(REFRESH)).thenReturn(member.getId());
        when(jwt.getAuthVersion(REFRESH)).thenReturn(member.getAuthVersion() + 1);
        CustomException failure = assertThrows(CustomException.class, () -> auth.refreshTokens(request));
        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, failure.getErrorCode());
        verifyNoTokenIssuance();
    }

    @Test
    void enabledOperatorRefreshStillRejectsSuspension() {
        enableOperator();
        Member member = operator();
        member.suspend(null, "Synthetic policy suspension", LocalDateTime.now());
        MockHttpServletRequest request = refreshRequest(member);
        CustomException failure = assertThrows(CustomException.class, () -> auth.refreshTokens(request));
        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, failure.getErrorCode());
        verifyNoTokenIssuance();
    }

    @Test
    void publicDescriptionRegistrationCannotMutateLocalAdministratorWithEitherFeatureState() {
        Member member = operator();
        member.setIntroduction("Original synthetic description");
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        for (boolean enabled : new boolean[]{false, true}) {
            ReflectionTestUtils.setField(auth, "adminAccountService", enabled ? mock(AdminAccountService.class) : null);
            CustomException failure = assertThrows(CustomException.class, () -> auth.registDesciption(MEMBER_ID,
                    new RegisterDescriptionRequest("Attempted synthetic replacement")));
            assertEquals(ErrorCode.MEMBER_NOT_FOUND, failure.getErrorCode());
            assertEquals("Original synthetic description", member.getIntroduction());
            assertEquals(UserRole.ADMIN, member.getRole());
        }
        verify(members, never()).save(any());
        verifyNoInteractions(jwt);
    }

    @Test
    void ordinaryTestAndStudentDescriptionRegistrationBehaviorRemainsUnchanged() {
        for (Member member : List.of(ordinaryTest(), student())) {
            when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));
            assertNotNull(auth.registDesciption(MEMBER_ID, new RegisterDescriptionRequest("Synthetic introduction")));
            assertEquals("Synthetic introduction", member.getIntroduction());
            assertEquals(UserRole.USER, member.getRole());
        }
        verifyNoInteractions(jwt);
    }
}
