package com.wardk.meeteam_backend.github.account;

import com.wardk.meeteam_backend.domain.admin.mapper.AdminResponseMapper;
import com.wardk.meeteam_backend.domain.admin.policy.AdminActionGuard;
import com.wardk.meeteam_backend.domain.admin.repository.AdminAuditWriter;
import com.wardk.meeteam_backend.domain.admin.repository.AdminQueryRepository;
import com.wardk.meeteam_backend.domain.admin.service.AdminMemberActionService;

import com.wardk.meeteam_backend.domain.github.account.*;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthClient.Identity;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubOAuthStateService.Validated;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(excludeAutoConfiguration = org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = GithubAccountPersistenceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GithubAccountPersistenceTest {
    @Configuration @EntityScan("com.wardk.meeteam_backend.domain")
    @EnableJpaRepositories(basePackageClasses = MemberGithubAccountRepository.class)
    @Import({GithubAccountService.class, AdminMemberActionService.class, AdminActionGuard.class,
            AdminAuditWriter.class, AdminQueryRepository.class, AdminResponseMapper.class})
    static class Config {
        @Bean EntityManager entityManager(EntityManagerFactory factory) {
            return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
    }
    @Autowired EntityManager em;
    @Autowired GithubAccountService service;
    @Autowired MemberGithubAccountRepository repository;
    @Autowired AdminMemberActionService adminActions;
    @Autowired PlatformTransactionManager manager;
    @MockBean S3ImageUrlResolver images;
    private long memberId, otherId, adminId;
    private static final String VERIFIER = "v".repeat(43);
    private <T> T tx(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(manager).execute(status -> action.get());
    }
    @BeforeEach void seed() {
        tx(() -> {
            var member = Member.createForTest(UUID.randomUUID() + "@example.invalid", "회원");
            var other = Member.createForTest(UUID.randomUUID() + "@example.invalid", "다른 회원");
            var admin = Member.createForTest(UUID.randomUUID() + "@example.invalid", "관리자"); admin.changeRole(UserRole.ADMIN);
            em.persist(member); em.persist(other); em.persist(admin); em.flush();
            memberId = member.getId(); otherId = other.getId(); adminId = admin.getId(); return null;
        });
    }
    private Validated state(long id) {
        var context = service.context(id);
        return new Validated(id, context.authVersion(), context.linkVersion(), "/settings", VERIFIER);
    }
    private Identity identity(long id) { return new Identity(id, "unit-dev", "https://avatars.githubusercontent.com/u/1"); }
    private long githubId() { return memberId * 100 + 10; }
    private void code(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CustomException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
    @Test void unlinkedAccountIsNull() { assertThat(service.get(memberId)).isNull(); }
    @Test void storesIdentityAndReturnsOnlyFrontendFieldsWithSeoulOffset() {
        service.connect(state(memberId), identity(githubId()));
        var result = service.get(memberId);
        assertThat(result.login()).isEqualTo("unit-dev");
        assertThat(result.profileUrl()).isEqualTo("https://github.com/unit-dev");
        assertThat(result.connectedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        tx(() -> { assertThat(em.find(Member.class, memberId).getGithubLinkVersion()).isEqualTo(1); return null; });
    }
    @Test void loginRenameMatchesStableGithubIdAndKeepsConnectionTime() {
        service.connect(state(memberId), identity(githubId()));
        var connectedAt = service.get(memberId).connectedAt();
        service.connect(state(memberId), new Identity(githubId(), "renamed-dev", ""));
        assertThat(service.get(memberId).login()).isEqualTo("renamed-dev");
        assertThat(service.get(memberId).connectedAt()).isEqualTo(connectedAt);
    }
    @Test void replacingOwnLinkReleasesPreviousGithubId() {
        service.connect(state(memberId), identity(githubId()));
        service.connect(state(memberId), identity(githubId() + 1));
        service.connect(state(otherId), identity(githubId()));
        assertThat(service.get(otherId)).isNotNull();
    }
    @Test void duplicateGithubAccountCannotReplaceExistingLink() {
        service.connect(state(memberId), identity(githubId()));
        service.connect(state(otherId), identity(githubId() + 1));
        code(() -> service.connect(state(otherId), identity(githubId())), ErrorCode.GITHUB_ACCOUNT_TAKEN);
        tx(() -> { assertThat(repository.findById(otherId).orElseThrow().getGithubUserId()).isEqualTo(githubId() + 1); return null; });
    }
    @Test void uniqueConstraintPreventsBypassingService() {
        service.connect(state(memberId), identity(githubId()));
        assertThatThrownBy(() -> tx(() -> {
            repository.saveAndFlush(new MemberGithubAccount(em.find(Member.class, otherId), githubId(), "unit", "", LocalDateTime.now()));
            return null;
        })).isInstanceOf(RuntimeException.class);
        assertThat(service.get(otherId)).isNull();
    }
    @Test void disconnectIsIdempotentAndDoesNotRevokeMeeTeamTokens() {
        service.connect(state(memberId), identity(githubId()));
        long authVersion = service.context(memberId).authVersion();
        service.disconnect(memberId); service.disconnect(memberId);
        assertThat(service.get(memberId)).isNull();
        assertThat(service.context(memberId).authVersion()).isEqualTo(authVersion);
    }
    @Test void disconnectCancelsPendingFlowEvenIfNotLinkedYet() {
        var pending = state(memberId);
        service.disconnect(memberId);
        code(() -> service.connect(pending, identity(githubId())), ErrorCode.GITHUB_INVALID_STATE);
        assertThat(service.get(memberId)).isNull();
    }
    @Test void onlyOneOfSeveralPendingFlowsCanLink() {
        var first = state(memberId); var second = state(memberId);
        service.connect(first, identity(githubId()));
        code(() -> service.connect(second, identity(githubId() + 1)), ErrorCode.GITHUB_INVALID_STATE);
    }
    @Test void suspensionAndUnsuspensionInvalidatePreviouslyIssuedLinkState() {
        var pending = state(memberId);
        tx(() -> { em.find(Member.class, memberId).suspend(null, "정지", LocalDateTime.now()); return null; });
        code(() -> service.connect(pending, identity(githubId())), ErrorCode.UNAUTHORIZED);
        tx(() -> { em.find(Member.class, memberId).unsuspend(); return null; });
        code(() -> service.connect(pending, identity(githubId())), ErrorCode.GITHUB_INVALID_STATE);
    }
    @Test void withdrawnMemberCannotLink() {
        var pending = state(memberId);
        tx(() -> { em.find(Member.class, memberId).withdraw(); return null; });
        code(() -> service.connect(pending, identity(githubId())), ErrorCode.UNAUTHORIZED);
    }
    @Test void administratorWithdrawalRemovesAccountAtomically() {
        service.connect(state(memberId), identity(githubId()));
        adminActions.withdraw(adminId, memberId, "운영 정책 위반 확인");
        tx(() -> {
            assertThat(repository.findById(memberId)).isEmpty();
            assertThat(em.find(Member.class, memberId).isAdminWithdrawn()).isTrue(); return null;
        });
    }
    @Test void differentMembersRacingForSameGithubIdHaveExactlyOneWinner() throws Exception {
        var a = state(memberId); var b = state(otherId);
        var start = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (var pending : List.of(a, b)) attempts.add(executor.submit(() -> {
                start.await();
                try { service.connect(pending, identity(githubId())); return true; }
                catch (RuntimeException expected) { return false; }
            }));
            start.countDown(); int winners = 0;
            for (var attempt : attempts) if (attempt.get(10, TimeUnit.SECONDS)) winners++;
            assertThat(winners).isEqualTo(1);
            assertThat(tx(() -> repository.findByGithubUserId(githubId()).isPresent())).isTrue();
        } finally { executor.shutdownNow(); }
    }
}
