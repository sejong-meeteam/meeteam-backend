package com.wardk.meeteam_backend.global.auth.service;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.UserRole;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.cookie.AccessTokenCookieProvider;
import com.wardk.meeteam_backend.global.auth.cookie.RefreshTokenCookieProvider;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import com.wardk.meeteam_backend.web.auth.controller.TestAccountController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestAccountServiceTest {
    private final String password = "unit-test-only-not-a-real-secret";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private MemberRepository members;
    private JwtUtil jwt;
    private TestAccountService service;

    @BeforeEach
    void setup() {
        members = mock(MemberRepository.class);
        jwt = mock(JwtUtil.class);
        service = new TestAccountService(members, encoder, jwt, password);
    }

    @Test
    void createsOnlyReservedUserWithHashedPassword() {
        when(members.findByEmail(TestAccountService.EMAIL)).thenReturn(Optional.empty());
        service.run(null);
        verify(members).save(argThat(member -> member.getEmail().equals(TestAccountService.EMAIL)
                && member.getRole() == UserRole.USER && encoder.matches(password, member.getPassword())
                && !password.equals(member.getPassword()) && member.getStudentId() == null));
    }

    @Test
    void preservesExistingTestAccount() {
        Member member = Member.createForTest(TestAccountService.EMAIL, "Test", encoder.encode(password));
        when(members.findByEmail(TestAccountService.EMAIL)).thenReturn(Optional.of(member));
        service.run(null);
        verify(members, never()).save(any());
    }

    @Test
    void refusesToOverwriteAccountWithDifferentPassword() {
        Member member = Member.createForTest(TestAccountService.EMAIL, "Test", encoder.encode("different"));
        when(members.findByEmail(TestAccountService.EMAIL)).thenReturn(Optional.of(member));
        assertThrows(IllegalStateException.class, () -> service.run(null));
        verify(members, never()).save(any());
    }

    @Test
    void acceptsReservedAccountAndIssuesTokens() {
        Member member = Member.createForTest(TestAccountService.EMAIL, "Test", encoder.encode(password));
        when(members.findByEmailAndDeletedAtIsNull(TestAccountService.EMAIL)).thenReturn(Optional.of(member));
        when(jwt.createAccessToken(member)).thenReturn("unit-test-access-placeholder");
        when(jwt.createRefreshToken(member)).thenReturn("unit-test-refresh-placeholder");
        assertNotNull(service.login(TestAccountService.USERNAME, password));
        verify(jwt).createAccessToken(member);
        verify(jwt).createRefreshToken(member);
    }

    @Test
    void wrongPasswordCannotIssueTokens() {
        Member member = Member.createForTest(TestAccountService.EMAIL, "Test", encoder.encode(password));
        when(members.findByEmailAndDeletedAtIsNull(TestAccountService.EMAIL)).thenReturn(Optional.of(member));
        assertThrows(CustomException.class, () -> service.login(TestAccountService.USERNAME, "wrong"));
        verifyNoInteractions(jwt);
    }

    @Test
    void cannotLoginAsOtherMember() {
        assertThrows(CustomException.class, () -> service.login("someone@example.invalid", password));
        verifyNoInteractions(members, jwt);
    }

    @Test
    void internalEmailIsNotAcceptedAsTheLoginId() {
        assertThrows(CustomException.class, () -> service.login(TestAccountService.EMAIL, password));
        verifyNoInteractions(members, jwt);
    }

    @Test
    void rateLimitsAttempts() {
        for (int i = 0; i < 11; i++) {
            assertThrows(CustomException.class, () -> service.login("wrong@example.invalid", "wrong"));
        }
        verifyNoInteractions(jwt);
    }

    @Test
    void rejectsWeakOrOverlongPassword() {
        assertThrows(IllegalArgumentException.class, () -> new TestAccountService(members, encoder, jwt, "short"));
        assertThrows(IllegalArgumentException.class, () -> new TestAccountService(members, encoder, jwt, "한".repeat(25)));
    }

    @Test
    void controllerSetsBothStandardCookiesWithoutReturningTokens() {
        TestAccountService accounts = mock(TestAccountService.class);
        AccessTokenCookieProvider access = mock(AccessTokenCookieProvider.class);
        RefreshTokenCookieProvider refresh = mock(RefreshTokenCookieProvider.class);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        when(accounts.login(TestAccountService.USERNAME, password)).thenReturn(
                new com.wardk.meeteam_backend.global.auth.service.dto.TokenExchangeResult(
                        "unit-test-access-placeholder", "unit-test-refresh-placeholder"));
        var controller = new TestAccountController(accounts, access, refresh);
        var result = controller.login(new TestAccountController.LoginRequest(TestAccountService.USERNAME, password), response);
        verify(access).addCookie(response, "unit-test-access-placeholder");
        verify(refresh).addCookie(response, "unit-test-refresh-placeholder");
        assertNull(result.getResult());
        assertFalse(new TestAccountController.LoginRequest(TestAccountService.USERNAME, password).toString().contains(password));
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withPropertyValues("jwt.access-exp-time=60000", "jwt.refresh-exp-time=120000",
                        "jwt.secret-key=unused-unit-test-placeholder")
                .withInitializer(ctx -> {
                    var placeholders = new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
                    placeholders.setEnvironment(ctx.getEnvironment());
                    ctx.addBeanFactoryPostProcessor(placeholders);
                })
                .withBean(MemberRepository.class, () -> members)
                .withBean(BCryptPasswordEncoder.class, () -> encoder)
                .withBean(JwtUtil.class, () -> jwt)
                .withBean(AccessTokenCookieProvider.class, () -> mock(AccessTokenCookieProvider.class))
                .withBean(RefreshTokenCookieProvider.class, () -> mock(RefreshTokenCookieProvider.class))
                .withUserConfiguration(TestAccountService.class, TestAccountController.class);
    }

    @Test
    void disabledByDefaultEvenInTestProfile() {
        context().withPropertyValues("spring.profiles.active=test").run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertEquals(0, ctx.getBeansOfType(TestAccountService.class).size());
            assertEquals(0, ctx.getBeansOfType(TestAccountController.class).size());
        });
    }

    @Test
    void unavailableOutsideTestAndInProductionEvenWhenEnabled() {
        for (String profile : new String[]{"prod", "production", "test,prod", "test,production", "dev"}) {
            context().withPropertyValues("spring.profiles.active=" + profile, "test-account.enabled=true")
                    .run(ctx -> {
                        assertNull(ctx.getStartupFailure());
                        assertEquals(0, ctx.getBeansOfType(TestAccountService.class).size());
                        assertEquals(0, ctx.getBeansOfType(TestAccountController.class).size());
                    });
        }
    }

    @Test
    void enabledOnlyWithTestProfileAndExplicitSwitchAndPassword() {
        context().withPropertyValues("spring.profiles.active=test", "test-account.enabled=true",
                "test-account.password=" + password).run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertEquals(1, ctx.getBeansOfType(TestAccountService.class).size());
                    assertEquals(1, ctx.getBeansOfType(TestAccountController.class).size());
                });
    }

    @Test
    void missingPasswordFailsClosed() {
        context().withPropertyValues("spring.profiles.active=test", "test-account.enabled=true")
                .run(ctx -> assertNotNull(ctx.getStartupFailure()));
    }
}
