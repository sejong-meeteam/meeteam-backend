package com.wardk.meeteam_backend.adminloginunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.UserRole;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.service.AdminAccountService;
import com.wardk.meeteam_backend.global.auth.service.TestAccountService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Local, isolated tests only. Synthetic values below are not deployment credentials. */
class AdminAccountServiceTest {
    private static final String USERNAME = "99123456";
    private final String password = "p".repeat(24);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private MemberRepository members;
    private JwtUtil jwt;
    private AdminAccountService service;

    @BeforeEach
    void setup() {
        members = mock(MemberRepository.class);
        jwt = mock(JwtUtil.class);
        service = service(password);
    }

    private AdminAccountService service(String configuredPassword) {
        return new AdminAccountService(members, encoder, jwt, USERNAME, configuredPassword);
    }

    private Member admin() {
        return Member.createForLocalAdmin(AdminAccountService.EMAIL, encoder.encode(password));
    }

    private void storedForLogin(Member member) {
        when(members.findByEmailAndDeletedAtIsNull(AdminAccountService.EMAIL)).thenReturn(Optional.of(member));
    }

    private void assertBadCredentials(Runnable login) {
        assertEquals(ErrorCode.BAD_CREDENTIALS, assertThrows(CustomException.class, login::run).getErrorCode());
    }

    @Test
    void createsOnlyReservedAdministratorWithHashAndNoStudentIdentity() {
        when(members.findByEmail(AdminAccountService.EMAIL)).thenReturn(Optional.empty());
        service.run(null);
        verify(members).findAllByStudentIdForUpdate(USERNAME);
        verify(members).save(argThat(member -> AdminAccountService.EMAIL.equals(member.getEmail())
                && member.getRole() == UserRole.ADMIN && "local-admin".equals(member.getProvider())
                && member.getStudentId() == null && !member.isAdminWithdrawn() && !member.isDeleted()
                && encoder.matches(password, member.getPassword()) && !password.equals(member.getPassword())));
    }

    @Test
    void stableRestartDoesNotWriteOrChangePasswordOrAuthorization() {
        Member member = admin();
        String originalHash = member.getPassword();
        long originalVersion = member.getAuthVersion();
        when(members.findByEmail(AdminAccountService.EMAIL)).thenReturn(Optional.of(member));
        service.run(null);
        service(password).run(null);
        verify(members, never()).save(any());
        assertEquals(originalHash, member.getPassword());
        assertEquals(originalVersion, member.getAuthVersion());
        assertEquals(UserRole.ADMIN, member.getRole());
        assertNull(member.getLastLoginAt());
    }

    @Test
    void bootstrapDoesNotResetPasswordWhenConfigurationChanges() {
        Member member = admin();
        String hash = member.getPassword();
        when(members.findByEmail(AdminAccountService.EMAIL)).thenReturn(Optional.of(member));
        assertThrows(IllegalStateException.class, () -> service("q".repeat(24)).run(null));
        assertEquals(hash, member.getPassword());
        verify(members, never()).save(any());
    }

    @Test
    void bootstrapRejectsIdentityRoleProviderStudentAndDeletedConflictsWithoutMutation() {
        for (String field : List.of("email", "role", "provider", "studentId", "deletedAt", "adminWithdrawn", "password")) {
            Member member = admin();
            Object changed = switch (field) {
                case "email" -> "other@example.invalid";
                case "role" -> UserRole.USER;
                case "provider" -> "sejong";
                case "studentId" -> USERNAME;
                case "deletedAt" -> LocalDateTime.now();
                case "adminWithdrawn" -> true;
                default -> null;
            };
            ReflectionTestUtils.setField(member, field, changed);
            long version = member.getAuthVersion();
            when(members.findByEmail(AdminAccountService.EMAIL)).thenReturn(Optional.of(member));
            assertThrows(IllegalStateException.class, () -> service.run(null), field);
            assertEquals(changed, ReflectionTestUtils.getField(member, field), field);
            assertEquals(version, member.getAuthVersion(), field);
        }
        verify(members, never()).save(any());
    }

    @Test
    void bootstrapRejectsEveryStudentCollisionIncludingDeletedAndAdministrativelyWithdrawn() {
        for (int state = 0; state < 3; state++) {
            Member collision = Member.createForTest("student@example.invalid", "Synthetic student");
            ReflectionTestUtils.setField(collision, "studentId", USERNAME);
            if (state == 1) collision.withdraw();
            if (state == 2) collision.forceWithdraw();
            when(members.findAllByStudentIdForUpdate(USERNAME)).thenReturn(List.of(collision));
            assertThrows(IllegalStateException.class, () -> service.run(null));
        }
        verify(members, never()).save(any());
        verify(members, never()).findByEmail(anyString());
    }

    @Test
    void bootstrapRetainsSuspensionAndLoginRejectsIt() {
        Member member = admin();
        member.suspend(null, "Synthetic suspension", LocalDateTime.now());
        long version = member.getAuthVersion();
        when(members.findByEmail(AdminAccountService.EMAIL)).thenReturn(Optional.of(member));
        storedForLogin(member);
        service.run(null);
        assertTrue(member.isSuspended());
        assertEquals(version, member.getAuthVersion());
        assertEquals(ErrorCode.MEMBER_SUSPENDED,
                assertThrows(CustomException.class, () -> service.login(USERNAME, password)).getErrorCode());
        assertNull(member.getLastLoginAt());
        verify(members, never()).save(any());
        verifyNoInteractions(jwt);
    }

    @Test
    void expiredSuspensionAllowsLoginWithoutReactivationOrVersionChange() {
        Member member = admin();
        member.suspend(1, "Expired synthetic suspension", LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(2));
        long version = member.getAuthVersion();
        storedForLogin(member);
        service.login(USERNAME, password);
        assertEquals(version, member.getAuthVersion());
        assertNotNull(member.getSuspendedUntil());
        verify(jwt).createAccessToken(member);
    }

    @Test
    void validLoginUsesExistingTokenFactoryAndRecordsSeoulTime() {
        Member member = admin();
        storedForLogin(member);
        when(jwt.createAccessToken(member)).thenReturn("synthetic-access");
        when(jwt.createRefreshToken(member)).thenReturn("synthetic-refresh");
        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusSeconds(1);
        var tokens = service.login(USERNAME, password);
        assertEquals("synthetic-access", tokens.getAccessToken());
        assertEquals("synthetic-refresh", tokens.getRefreshToken());
        assertTrue(member.getLastLoginAt().isAfter(before));
        assertTrue(member.getLastLoginAt().isBefore(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusSeconds(1)));
        verify(members, never()).save(any());
    }

    @Test
    void storedDatabasePasswordIsAuthoritativeForLogin() {
        Member member = admin();
        String replacement = "r".repeat(24);
        ReflectionTestUtils.setField(member, "password", encoder.encode(replacement));
        storedForLogin(member);
        assertBadCredentials(() -> service.login(USERNAME, password));
        service.login(USERNAME, replacement);
        verify(jwt).createAccessToken(member);
        verify(jwt).createRefreshToken(member);
    }

    @Test
    void wrongMissingAndOverlongPasswordsCannotIssueTokens() {
        Member member = admin();
        storedForLogin(member);
        for (String submitted : new String[]{"wrong", null, "", "x".repeat(73), "한".repeat(25)}) {
            assertBadCredentials(() -> service.login(USERNAME, submitted));
        }
        assertNull(member.getLastLoginAt());
        verifyNoInteractions(jwt);
    }

    @Test
    void onlyExactConfiguredUsernameMatchesAndInternalEmailCannotLogin() {
        assertTrue(service.matchesUsername(USERNAME));
        for (String candidate : new String[]{null, " " + USERNAME, USERNAME + " ", "99123457",
                TestAccountService.USERNAME, AdminAccountService.EMAIL}) {
            assertFalse(service.matchesUsername(candidate));
            assertBadCredentials(() -> service(password).login(candidate, password));
        }
        verifyNoInteractions(members, jwt);
    }

    @Test
    void absentMemberAndInvalidIdentitiesPerformDummyHashWorkNotStoredHashMatching() {
        BCryptPasswordEncoder tracking = spy(new BCryptPasswordEncoder(4));
        AdminAccountService tracked = new AdminAccountService(members, tracking, jwt, USERNAME, password);
        String dummyHash = (String) ReflectionTestUtils.getField(tracked, "dummyHash");
        assertBadCredentials(() -> tracked.login(USERNAME, password));
        Member demoted = admin();
        demoted.changeRole(UserRole.USER);
        storedForLogin(demoted);
        assertBadCredentials(() -> tracked.login(USERNAME, password));
        assertBadCredentials(() -> tracked.login("other", password));
        verify(tracking, times(3)).matches(password, dummyHash);
        verify(tracking, never()).matches(password, demoted.getPassword());
        verifyNoInteractions(jwt);
    }

    @Test
    void invalidStoredIdentityNeverAuthenticatesEvenWithCorrectPassword() {
        for (String field : List.of("email", "role", "provider", "studentId", "deletedAt", "adminWithdrawn", "password")) {
            Member member = admin();
            Object value = switch (field) {
                case "email" -> "other@example.invalid";
                case "role" -> UserRole.USER;
                case "provider" -> null;
                case "studentId" -> USERNAME;
                case "deletedAt" -> LocalDateTime.now();
                case "adminWithdrawn" -> true;
                default -> null;
            };
            ReflectionTestUtils.setField(member, field, value);
            storedForLogin(member);
            assertBadCredentials(() -> service(password).login(USERNAME, password));
            assertNull(member.getLastLoginAt());
        }
        verifyNoInteractions(jwt);
    }

    @Test
    void fiveSuccessfulAttemptsConsumeWholeWindowAndRolloverAllowsAnother() {
        Member member = admin();
        storedForLogin(member);
        for (int i = 0; i < 5; i++) service.login(USERNAME, password);
        assertBadCredentials(() -> service.login(USERNAME, password));
        verify(jwt, times(5)).createAccessToken(member);
        ReflectionTestUtils.setField(service, "attemptWindow", System.nanoTime() - 60_000_000_001L);
        service.login(USERNAME, password);
        verify(jwt, times(6)).createAccessToken(member);
    }

    @Test
    void failedAttemptsAlsoConsumeWindowAndParallelCallsCannotExceedFive() throws Exception {
        for (int i = 0; i < 5; i++) assertBadCredentials(() -> service.login("wrong", password));
        storedForLogin(admin());
        assertBadCredentials(() -> service.login(USERNAME, password));
        verifyNoInteractions(jwt);
        AdminAccountService concurrent = service(password);
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = IntStream.range(0, 20).mapToObj(i -> (Callable<Boolean>) () -> {
                try {
                    concurrent.login(USERNAME, password);
                    return true;
                } catch (CustomException failure) {
                    assertEquals(ErrorCode.BAD_CREDENTIALS, failure.getErrorCode());
                    return false;
                }
            }).toList();
            long successes = 0;
            for (var result : pool.invokeAll(calls)) if (result.get()) successes++;
            assertEquals(5, successes);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void validatesNumericUsernameAndPasswordCharacterAndUtf8Bounds() {
        for (String invalid : new String[]{null, "", "9912345", "991234567", "98123456", "99abcdef",
                "９９１２３４５６", TestAccountService.USERNAME}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AdminAccountService(members, encoder, jwt, invalid, password));
        }
        for (String invalid : new String[]{null, "p".repeat(19), "p".repeat(73), "한".repeat(25)}) {
            assertThrows(IllegalArgumentException.class, () -> service(invalid));
        }
        assertDoesNotThrow(() -> service("p".repeat(20)));
        assertDoesNotThrow(() -> service("p".repeat(72)));
        assertDoesNotThrow(() -> service("한".repeat(24)));
        verifyNoInteractions(members, jwt);
    }

    @Test
    void runnerRequiresSerializableBootstrapAndExplicitEnablement() throws Exception {
        assertTrue(ApplicationRunner.class.isAssignableFrom(AdminAccountService.class));
        Transactional tx = AdminAccountService.class.getMethod("run", ApplicationArguments.class).getAnnotation(Transactional.class);
        assertEquals(Isolation.SERIALIZABLE, tx.isolation());
        ConditionalOnProperty condition = AdminAccountService.class.getAnnotation(ConditionalOnProperty.class);
        assertArrayEquals(new String[]{"admin-account.enabled"}, condition.name());
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
        assertNotNull(AdminAccountService.class.getMethod("login", String.class, String.class).getAnnotation(Transactional.class));
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withPropertyValues("jwt.secret-key=synthetic-context-only-signing-key-never-deployed",
                        "jwt.access-exp-time=3600000", "jwt.refresh-exp-time=1209600000")
                .withInitializer(ctx -> {
                    var placeholders = new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
                    placeholders.setEnvironment(ctx.getEnvironment());
                    ctx.addBeanFactoryPostProcessor(placeholders);
                })
                .withBean(MemberRepository.class, () -> members)
                .withBean(BCryptPasswordEncoder.class, () -> encoder)
                .withBean(JwtUtil.class, () -> jwt)
                .withUserConfiguration(AdminAccountService.class);
    }

    @Test
    void disabledByDefaultOrExplicitlyFalseWithoutRequiringCredentials() {
        context().run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertTrue(ctx.getBeansOfType(AdminAccountService.class).isEmpty());
        });
        context().withPropertyValues("admin-account.enabled=false").run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertTrue(ctx.getBeansOfType(AdminAccountService.class).isEmpty());
        });
    }

    @Test
    void hashOnlyConfigurationDoesNotRequirePlaintextPassword() {
        String hash = new BCryptPasswordEncoder(10).encode(password);
        context().withPropertyValues("admin-account.enabled=true", "admin-account.username=" + USERNAME,
                "admin-account.password-hash=" + hash).run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertEquals(1, ctx.getBeansOfType(AdminAccountService.class).size());
                });
        verify(members, never()).save(any());
    }

    @Test
    void enabledConditionRequiresBothConfiguredValues() {
        context().withPropertyValues("admin-account.enabled=true").run(ctx -> assertNotNull(ctx.getStartupFailure()));
        context().withPropertyValues("admin-account.enabled=true", "admin-account.username=" + USERNAME)
                .run(ctx -> assertNotNull(ctx.getStartupFailure()));
        context().withPropertyValues("admin-account.enabled=true", "admin-account.username=" + USERNAME,
                "admin-account.password=" + password).run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertEquals(1, ctx.getBeansOfType(AdminAccountService.class).size());
                });
    }
}
