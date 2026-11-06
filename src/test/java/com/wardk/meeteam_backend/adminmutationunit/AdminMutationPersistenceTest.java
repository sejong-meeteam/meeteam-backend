package com.wardk.meeteam_backend.adminmutationunit;

import com.wardk.meeteam_backend.domain.admin.dto.AdminQueryOptions;
import com.wardk.meeteam_backend.domain.admin.mapper.AdminResponseMapper;
import com.wardk.meeteam_backend.domain.admin.policy.AdminActionGuard;
import com.wardk.meeteam_backend.domain.admin.repository.AdminAuditWriter;
import com.wardk.meeteam_backend.domain.admin.repository.AdminQueryRepository;
import com.wardk.meeteam_backend.domain.admin.service.AdminAuditQueryService;
import com.wardk.meeteam_backend.domain.admin.service.AdminMutationService;

import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(excludeAutoConfiguration = org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
 properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = AdminMutationPersistenceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminMutationPersistenceTest {
    @Configuration @EntityScan("com.wardk.meeteam_backend.domain")
    @Import({AdminMutationService.class, AdminActionGuard.class, AdminAuditWriter.class, AdminQueryRepository.class,
            AdminResponseMapper.class, AdminAuditQueryService.class})
    static class Config {
        @Bean EntityManager entityManager(EntityManagerFactory factory) {
            return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
    }
    @Autowired EntityManager em;
    @Autowired AdminMutationService service;
    @Autowired AdminAuditQueryService logs;
    @Autowired PlatformTransactionManager manager;
    @MockBean S3ImageUrlResolver images;
    @SpyBean AdminAuditWriter writer;
    private long adminId, memberId, projectId;

    private <T> T tx(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(manager).execute(status -> action.get());
    }
    @BeforeEach void seed() {
        reset((AdminAuditWriter) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(writer));
        tx(() -> {
            Member admin = Member.createForTest(UUID.randomUUID() + "@example.invalid", "관리자");
            ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
            Member member = Member.createForTest(UUID.randomUUID() + "@example.invalid", "회원");
            em.persist(admin); em.persist(member);
            Project project = Project.builder().creator(member).name("관리 대상").description("소개")
                    .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
                    .recruitmentStatus(Recruitment.RECRUITING)
                    .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE).build();
            em.persist(project); em.flush();
            adminId = admin.getId(); memberId = member.getId(); projectId = project.getId();
            return null;
        });
    }
    @Test void suspensionAndAuditCommitTogetherAndPermanentMetadataKeepsNull() {
        var result = service.suspend(adminId, memberId, null, "  운영 정책 위반  ");
        assertThat(result.status()).isEqualTo("SUSPENDED");
        assertThat(result.suspendedUntil()).isNull();
        tx(() -> {
            Member member = em.find(Member.class, memberId);
            assertThat(member.isSuspended()).isTrue();
            assertThat(member.getAuthVersion()).isEqualTo(1);
            assertThat(member.getSuspendReason()).isEqualTo("운영 정책 위반");
            return null;
        });
        var page = logs.logs(AdminQueryOptions.of(null,null,null,null), "MEMBER_SUSPEND", "MEMBER", "" + memberId);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).metadata()).containsEntry("days", null);
        service.unsuspend(adminId, memberId, "정지 해제 확인 완료");
        tx(() -> { assertThat(em.find(Member.class, memberId).isSuspended()).isFalse(); return null; });
    }
    @Test void auditFailureRollsBackSuspensionAndAuthVersion() {
        doThrow(new IllegalStateException("simulated audit storage failure")).when((AdminAuditWriter) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(writer)).append(any());
        assertThatThrownBy(() -> service.suspend(adminId, memberId, 7, "운영 정책 위반 확인"))
                .isInstanceOf(IllegalStateException.class);
        tx(() -> {
            Member member = em.find(Member.class, memberId);
            assertThat(member.isSuspended()).isFalse(); assertThat(member.getAuthVersion()).isZero();
            return null;
        });
        assertThat(logs.logs(AdminQueryOptions.of(null,null,null,null), null, "MEMBER", "" + memberId).totalElements()).isZero();
    }
    @Test void hideDeleteRestoreKeepRecruitmentAndWriteRealAuditRows() {
        assertThat(service.visibility(adminId, projectId, ProjectVisibility.HIDDEN, "노출 중단 요청 확인").visibility()).isEqualTo("HIDDEN");
        assertThat(service.visibility(adminId, projectId, ProjectVisibility.DELETED, "삭제 요청 내용 확인").visibility()).isEqualTo("DELETED");
        assertThat(service.visibility(adminId, projectId, ProjectVisibility.VISIBLE, "복구 요청 내용 확인").visibility()).isEqualTo("VISIBLE");
        tx(() -> {
            Project project = em.find(Project.class, projectId);
            assertThat(project.isDeleted()).isFalse();
            assertThat(project.getRecruitmentStatus()).isEqualTo(Recruitment.RECRUITING);
            return null;
        });
        var page = logs.logs(AdminQueryOptions.of(null,null,null,null), null, "PROJECT", "" + projectId);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content().get(0).action()).isEqualTo("PROJECT_RESTORE");
        assertThat(page.content().get(0).metadata()).containsEntry("from", "DELETED").containsEntry("to", "VISIBLE");
    }
    @Test void auditFailureAlsoRollsBackVisibility() {
        doThrow(new IllegalStateException("simulated audit failure")).when((AdminAuditWriter) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(writer)).append(any());
        assertThatThrownBy(() -> service.visibility(adminId, projectId, ProjectVisibility.HIDDEN, "비공개 조치 사유 확인"))
                .isInstanceOf(IllegalStateException.class);
        tx(() -> { assertThat(em.find(Project.class, projectId).getVisibility()).isEqualTo(ProjectVisibility.VISIBLE); return null; });
    }
    @Test void rejectsSelfActionWrongActorRepeatedStateAndMissingTargets() {
        assertCode(() -> service.suspend(adminId, adminId, 1, "본인 정지 요청 확인"), ErrorCode.ADMIN_SELF_ACTION);
        assertCode(() -> service.suspend(memberId, adminId, 1, "권한 없는 요청 확인"), ErrorCode.ADMIN_FORBIDDEN);
        assertCode(() -> service.unsuspend(adminId, memberId, "이미 활성 상태 확인"), ErrorCode.ADMIN_INVALID_STATE);
        assertCode(() -> service.visibility(adminId, projectId, ProjectVisibility.VISIBLE, "이미 같은 상태 확인"), ErrorCode.ADMIN_INVALID_STATE);
        assertCode(() -> service.suspend(adminId, Long.MAX_VALUE, 1, "대상 없는 요청 확인"), ErrorCode.ADMIN_TARGET_NOT_FOUND);
        service.suspend(adminId, memberId, 1, "첫 번째 정지 조치");
        assertCode(() -> service.suspend(adminId, memberId, 1, "중복 정지 조치 요청"), ErrorCode.ADMIN_INVALID_STATE);
    }
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test void concurrentAdminsCannotSuspendEachOtherSuccessfully() throws Exception {
        long secondAdmin = tx(() -> {
            Member member = Member.createForTest(UUID.randomUUID() + "@example.invalid", "다른 관리자");
            ReflectionTestUtils.setField(member, "role", UserRole.ADMIN);
            em.persist(member); em.flush(); return member.getId();
        });
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return suspendOutcome(adminId, secondAdmin); });
            var second = pool.submit(() -> { start.await(); return suspendOutcome(secondAdmin, adminId); });
            start.countDown();
            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ok", "ADMIN403");
            tx(() -> {
                assertThat(java.util.List.of(em.find(Member.class, adminId), em.find(Member.class, secondAdmin)))
                        .filteredOn(m -> !m.isSuspended()).hasSize(1);
                return null;
            });
        } finally { pool.shutdownNow(); }
    }
    private String suspendOutcome(long actor, long target) {
        try { service.suspend(actor, target, 1, "동시 관리자 조치 검증"); return "ok"; }
        catch (CustomException exception) { return exception.getErrorCode().getCode(); }
    }
}
