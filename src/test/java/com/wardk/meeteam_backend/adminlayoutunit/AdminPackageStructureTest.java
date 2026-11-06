package com.wardk.meeteam_backend.adminlayoutunit;

import com.wardk.meeteam_backend.domain.admin.entity.AdminAuditLog;
import com.wardk.meeteam_backend.domain.admin.service.AdminMemberActionService;
import com.wardk.meeteam_backend.domain.admin.service.AdminProjectActionService;
import com.wardk.meeteam_backend.domain.github.integration.GithubLifecycleAspect;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Package-only refactoring contracts. No external services or credentials are used. */
class AdminPackageStructureTest {
    private static final String ROOT = "com.wardk.meeteam_backend.";
    private static final Map<String, String> PACKAGES = Map.ofEntries(
            Map.entry("AdminQueryController", "web.admin.controller"),
            Map.entry("AdminMutationController", "web.admin.controller"),
            Map.entry("AdminAuditController", "web.admin.controller"),
            Map.entry("AdminMutationRequests", "web.admin.dto.request"),
            Map.entry("AdminResponses", "web.admin.dto.response"),
            Map.entry("AdminRequestExceptionHandler", "web.admin.exception"),
            Map.entry("AdminQueryService", "domain.admin.service"),
            Map.entry("AdminDashboardService", "domain.admin.service"),
            Map.entry("AdminMutationService", "domain.admin.service"),
            Map.entry("AdminMemberActionService", "domain.admin.service"),
            Map.entry("AdminProjectActionService", "domain.admin.service"),
            Map.entry("AdminAuditQueryService", "domain.admin.service"),
            Map.entry("AdminQueryRepository", "domain.admin.repository"),
            Map.entry("AdminDashboardRepository", "domain.admin.repository"),
            Map.entry("AdminAuditWriter", "domain.admin.repository"),
            Map.entry("AdminAuditLog", "domain.admin.entity"),
            Map.entry("AuditAction", "domain.admin.entity"),
            Map.entry("AuditTargetType", "domain.admin.entity"),
            Map.entry("AdminActionGuard", "domain.admin.policy"),
            Map.entry("AdminReason", "domain.admin.policy"),
            Map.entry("AdminResponseMapper", "domain.admin.mapper"),
            Map.entry("AdminQueryOptions", "domain.admin.dto"),
            Map.entry("AdminTimeConfig", "domain.admin.config")
    );

    @Test
    void allAdminTypesUseTheRoleBasedPackages() throws Exception {
        for (var entry : PACKAGES.entrySet()) {
            Class<?> type = Class.forName(ROOT + entry.getValue() + "." + entry.getKey());
            assertEquals(ROOT + entry.getValue(), type.getPackageName());
        }
    }

    @Test
    void oldFlatPackagesDoNotLeaveDuplicateTypesOnTheClasspath() {
        for (var entry : PACKAGES.entrySet()) {
            String previous = ROOT + (entry.getValue().startsWith("web.") ? "web.admin." : "domain.admin.") + entry.getKey();
            assertThrows(ClassNotFoundException.class, () -> Class.forName(previous));
        }
    }

    @Test
    void lifecyclePointcutStillMatchesBothRelocatedAdministrativeActions() throws Exception {
        String expression = GithubLifecycleAspect.class.getMethod("lifecycle", ProceedingJoinPoint.class)
                .getAnnotation(Around.class).value();
        var pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(expression);
        assertTrue(pointcut.matches(AdminProjectActionService.class.getMethod("expel", Long.class, long.class, long.class, String.class),
                AdminProjectActionService.class));
        assertTrue(pointcut.matches(AdminMemberActionService.class.getMethod("withdraw", Long.class, long.class, String.class),
                AdminMemberActionService.class));
        assertFalse(pointcut.matches(AdminProjectActionService.class.getMethod("close", Long.class, long.class, String.class),
                AdminProjectActionService.class));
    }

    @Test
    void auditEntityAndTableNamesRemainUnchangedForJpaQueriesAndExistingData() {
        assertEquals("", AdminAuditLog.class.getAnnotation(Entity.class).name());
        assertEquals("AdminAuditLog", AdminAuditLog.class.getSimpleName());
        assertEquals("admin_audit_log", AdminAuditLog.class.getAnnotation(Table.class).name());
    }
}
