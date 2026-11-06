package com.wardk.meeteam_backend.adminsecurityunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.UserRole;
import com.wardk.meeteam_backend.global.auth.repository.TokenBlacklistRepository;
import com.wardk.meeteam_backend.global.auth.service.CustomUserDetailsService;
import com.wardk.meeteam_backend.global.config.CorsProperties;
import com.wardk.meeteam_backend.global.config.SecurityConfig;
import com.wardk.meeteam_backend.global.config.SecurityUrls;
import com.wardk.meeteam_backend.global.exception.RestAccessDeniedHandler;
import com.wardk.meeteam_backend.global.exception.RestAuthenticationEntryPoint;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminSecurityContractTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityUrls.class, RestAccessDeniedHandler.class,
        RestAuthenticationEntryPoint.class, AdminSecurityContractTest.CorsConfig.class,
        AdminSecurityContractTest.ProbeController.class,
        com.wardk.meeteam_backend.web.admin.controller.AdminQueryController.class,
        com.wardk.meeteam_backend.web.admin.controller.AdminMutationController.class,
        com.wardk.meeteam_backend.web.admin.controller.AdminAuditController.class,
        com.wardk.meeteam_backend.web.admin.exception.AdminRequestExceptionHandler.class})
class AdminSecurityContractTest {
    @Autowired MockMvc mvc;
    @MockBean com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver images;
    @MockBean com.wardk.meeteam_backend.domain.admin.service.AdminQueryService adminQueries;
    @MockBean com.wardk.meeteam_backend.domain.admin.service.AdminMutationService mutations;
    @MockBean com.wardk.meeteam_backend.domain.admin.service.AdminAuditQueryService auditQueries;
    @MockBean com.wardk.meeteam_backend.domain.admin.service.AdminMemberActionService memberActions;
    @MockBean com.wardk.meeteam_backend.domain.admin.service.AdminProjectActionService projectActions;
    @MockBean com.wardk.meeteam_backend.domain.admin.service.AdminDashboardService dashboard;
    @MockBean JwtUtil jwt;
    @MockBean CustomUserDetailsService users;
    @MockBean TokenBlacklistRepository blacklist;

    @TestConfiguration
    static class CorsConfig {
        @Bean CorsProperties corsProperties() {
            var properties = new CorsProperties();
            properties.setAllowedOrigins(List.of("https://frontend.example.invalid"));
            properties.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            properties.setAllowedHeaders(List.of("*"));
            properties.setExposedHeaders(List.of());
            return properties;
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/api/v1/admin/probe", "/api/v1/probe", "/api/v1/administrator"})
        String probe() { return "ok"; }
    }

    private void authenticate(UserRole role) {
        Member member = Member.createForTest("unit@example.invalid", "테스트 회원");
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(member, "id", 1L);
        when(jwt.getMemberId("test-access")).thenReturn(1L);
        when(jwt.getCategory("test-access")).thenReturn(JwtUtil.ACCESS_CATEGORY);
        when(jwt.isExpired("test-access")).thenReturn(false);
        when(jwt.getUsername("test-access")).thenReturn(member.getEmail());
        when(users.loadUserByUsername(member.getEmail())).thenReturn(new CustomSecurityUserDetails(member));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin", "/api/v1/admin/probe", "/api/v1/admin/future/endpoint"})
    void anonymousCannotEnterAdminNamespace(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.result").value(nullValue()));
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
    void userCannotUseAnyAdminVerb(String method) throws Exception {
        authenticate(UserRole.USER);
        mvc.perform(request(HttpMethod.valueOf(method), "/api/v1/admin/probe")
                        .header("Authorization", "Bearer test-access"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN403"))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }

    @Test
    void adminCanEnter() throws Exception {
        authenticate(UserRole.ADMIN);
        mvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer test-access"))
                .andExpect(status().isOk()).andExpect(content().string("ok"));
    }

    @Test
    void currentDatabaseRoleOverridesOldTokenRole() throws Exception {
        authenticate(UserRole.USER);
        when(jwt.getRole("test-access")).thenReturn("ADMIN");
        mvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer test-access"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN403"));
        verify(jwt, never()).getRole(anyString());
    }

    @Test
    void existingMemberEndpointIsNotAdminOnly() throws Exception {
        authenticate(UserRole.USER);
        mvc.perform(get("/api/v1/probe").header("Authorization", "Bearer test-access"))
                .andExpect(status().isOk());
    }

    @Test
    void similarlyNamedPathIsNotAdminNamespace() throws Exception {
        authenticate(UserRole.USER);
        mvc.perform(get("/api/v1/administrator").header("Authorization", "Bearer test-access"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin/members", "/api/v1/admin/members/1", "/api/v1/admin/projects", "/api/v1/admin/projects/1"})
    void realAdminQueriesAreDeniedBeforeServiceForUser(String path) throws Exception {
        authenticate(UserRole.USER);
        mvc.perform(get(path).header("Authorization", "Bearer test-access"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN403"));
        verifyNoInteractions(adminQueries);
    }

    @Test
    void adminCanReadAnEmptyMemberPageWithContractMetadata() throws Exception {
        authenticate(UserRole.ADMIN);
        when(adminQueries.members(any(), isNull(), isNull(), isNull())).thenReturn(
                com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.Page.of(List.of(), 0, 10, 0));
        mvc.perform(get("/api/v1/admin/members").header("Authorization", "Bearer test-access"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.content").isArray())
                .andExpect(jsonPath("$.result.number").value(0))
                .andExpect(jsonPath("$.result.size").value(10))
                .andExpect(jsonPath("$.result.totalElements").value(0))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(true));
    }

    @Test
    void invalidAdminPaginationReturnsAdminErrorWithoutQuerying() throws Exception {
        authenticate(UserRole.ADMIN);
        mvc.perform(get("/api/v1/admin/members?page=-1").header("Authorization", "Bearer test-access"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_INVALID_REQUEST"))
                .andExpect(jsonPath("$.result").value(nullValue()));
        verifyNoInteractions(adminQueries);
    }

    @Test
    void missingAdminTargetReturnsContract404() throws Exception {
        authenticate(UserRole.ADMIN);
        when(adminQueries.member(999L)).thenThrow(new com.wardk.meeteam_backend.global.exception.CustomException(
                com.wardk.meeteam_backend.global.response.ErrorCode.ADMIN_TARGET_NOT_FOUND));
        mvc.perform(get("/api/v1/admin/members/999").header("Authorization", "Bearer test-access"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN_TARGET_NOT_FOUND"))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }

    @Test
    void adminCorsPreflightDoesNotRequireLogin() throws Exception {
        mvc.perform(options("/api/v1/admin/members")
                        .header("Origin", "https://frontend.example.invalid")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.invalid"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "{\"days\":null,\"reason\":\"짧음\"}", "{\"reason\":\"운영 정책 위반 확인\"}",
            "{\"days\":0,\"reason\":\"운영 정책 위반 확인\"}", "{\"days\":366,\"reason\":\"운영 정책 위반 확인\"}",
            "{\"days\":1.5,\"reason\":\"운영 정책 위반 확인\"}", "{\"days\":\"7\",\"reason\":\"운영 정책 위반 확인\"}"})
    void adminMutationRejectsMalformedAndCoercedBodies(String body) throws Exception {
        authenticate(UserRole.ADMIN);
        mvc.perform(post("/api/v1/admin/members/2/suspend").header("Authorization", "Bearer test-access")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADMIN_INVALID_REQUEST"))
                .andExpect(jsonPath("$.result").value(nullValue()));
        verifyNoInteractions(mutations);
    }

    @Test
    void adminMutationTrimsReasonAndAcceptsExplicitNullDays() throws Exception {
        authenticate(UserRole.ADMIN);
        mvc.perform(post("/api/v1/admin/members/2/suspend").header("Authorization", "Bearer test-access")
                        .contentType("application/json").content("{\"days\":null,\"reason\":\"  운영 정책 위반 확인  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON200"));
        verify(mutations).suspend(1L, 2L, null, "운영 정책 위반 확인");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin/members/2/suspend", "/api/v1/admin/members/2/unsuspend", "/api/v1/admin/projects/2/visibility"})
    void userCannotChangeModerationState(String path) throws Exception {
        authenticate(UserRole.USER);
        var request = path.endsWith("visibility") ? patch(path) : post(path);
        mvc.perform(request.header("Authorization", "Bearer test-access").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN403"));
        verifyNoInteractions(mutations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"role\":null,\"reason\":\"권한 변경 사유 확인\"}",
            "{\"role\":true,\"reason\":\"권한 변경 사유 확인\"}", "{\"role\":\"ROOT\",\"reason\":\"권한 변경 사유 확인\"}",
            "{\"role\":\"ADMIN\",\"reason\":\"짧음\"}"})
    void invalidRoleBodiesNeverReachMemberService(String body) throws Exception {
        authenticate(UserRole.ADMIN);
        mvc.perform(patch("/api/v1/admin/members/2/role").header("Authorization", "Bearer test-access")
                .contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADMIN_INVALID_REQUEST"));
        verifyNoInteractions(memberActions);
    }
    @Test
    void validRoleBodyUsesTypedRoleAndTrimmedReason() throws Exception {
        authenticate(UserRole.ADMIN);
        mvc.perform(patch("/api/v1/admin/members/2/role").header("Authorization", "Bearer test-access")
                .contentType("application/json").content("{\"role\":\"ADMIN\",\"reason\":\"  권한 변경 사유 확인  \"}"))
                .andExpect(status().isOk());
        verify(memberActions).changeRole(1L, 2L, UserRole.ADMIN, "권한 변경 사유 확인");
    }
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin/members/2/role", "/api/v1/admin/members/2/withdraw",
            "/api/v1/admin/projects/2/close", "/api/v1/admin/projects/2/members/3/expel"})
    void ordinaryUsersCannotCallPhaseThreeActions(String path) throws Exception {
        authenticate(UserRole.USER);
        var request = path.endsWith("role") ? patch(path) : post(path);
        mvc.perform(request.header("Authorization", "Bearer test-access").contentType("application/json")
                .content("{\"role\":\"ADMIN\",\"reason\":\"관리자 조치 사유 확인\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN403"));
        verifyNoInteractions(memberActions, projectActions);
    }
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/admin/members/2/withdraw", "/api/v1/admin/projects/2/close",
            "/api/v1/admin/projects/2/members/3/expel"})
    void phaseThreeActionsRequireReasonBeforeService(String path) throws Exception {
        authenticate(UserRole.ADMIN);
        mvc.perform(post(path).header("Authorization", "Bearer test-access").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADMIN_INVALID_REQUEST"));
        verifyNoInteractions(memberActions, projectActions);
    }

    @Test
    void concurrentAdminActionReturnsSafeRetryableConflict() throws Exception {
        authenticate(UserRole.ADMIN);
        when(memberActions.changeRole(anyLong(), anyLong(), any(), anyString())).thenThrow(
                new org.springframework.orm.ObjectOptimisticLockingFailureException("internal-entity-debug", 2L));
        mvc.perform(patch("/api/v1/admin/members/2/role").header("Authorization", "Bearer test-access")
                .contentType("application/json").content("{\"role\":\"ADMIN\",\"reason\":\"권한 변경 사유 확인\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_INVALID_STATE"))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-entity-debug"))));
    }
    @Test
    void auditStorageFailureDoesNotExposeDatabaseDiagnostics() throws Exception {
        authenticate(UserRole.ADMIN);
        when(memberActions.withdraw(anyLong(), anyLong(), anyString())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("internal-database-debug"));
        mvc.perform(post("/api/v1/admin/members/2/withdraw").header("Authorization", "Bearer test-access")
                .contentType("application/json").content("{\"reason\":\"강제 탈퇴 사유 확인\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("COMMON500"))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-database-debug"))));
    }

    @Test
    void dashboardAnonymousIs401AndOrdinaryMemberIs403WithoutDatabaseAccess() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
        authenticate(UserRole.USER);
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer test-access"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN403"));
        verifyNoInteractions(dashboard);
    }
    @Test
    void dashboardResponseHasExactlyTheFrontendTopLevelAndStatsFields() throws Exception {
        authenticate(UserRole.ADMIN);
        when(dashboard.dashboard()).thenReturn(new com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.Dashboard(
                new com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.DashboardStats(1,2,3,4,5,6,7), List.of(), List.of(), List.of()));
        var result = mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer test-access"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.stats.totalMembers").value(1))
                .andExpect(jsonPath("$.result.stats.newMembersToday").value(2))
                .andExpect(jsonPath("$.result.stats.suspendedMembers").value(3))
                .andExpect(jsonPath("$.result.stats.totalProjects").value(4))
                .andExpect(jsonPath("$.result.stats.recruitingProjects").value(5))
                .andExpect(jsonPath("$.result.stats.hiddenProjects").value(6))
                .andExpect(jsonPath("$.result.stats.pendingApplications").value(7))
                .andExpect(jsonPath("$.result.recentMembers").isArray())
                .andExpect(jsonPath("$.result.recentProjects").isArray())
                .andExpect(jsonPath("$.result.recentAuditLogs").isArray()).andReturn();
        var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("result");
        org.junit.jupiter.api.Assertions.assertEquals(4, data.size());
        org.junit.jupiter.api.Assertions.assertEquals(7, data.get("stats").size());
    }
    @Test
    void encodedDashboardSortLinkIsDecodedByMvcBeforeRepositoryComparison() throws Exception {
        authenticate(UserRole.ADMIN);
        when(adminQueries.projects(any(), isNull(), isNull(), isNull())).thenReturn(
                com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.Page.of(List.of(),0,10,0));
        mvc.perform(get(java.net.URI.create("/api/v1/admin/projects?sort=pendingApplicationCount%2Cdesc"))
                .header("Authorization", "Bearer test-access")).andExpect(status().isOk());
        var options = org.mockito.ArgumentCaptor.forClass(com.wardk.meeteam_backend.domain.admin.dto.AdminQueryOptions.class);
        verify(adminQueries).projects(options.capture(), isNull(), isNull(), isNull());
        org.junit.jupiter.api.Assertions.assertEquals("pendingApplicationCount,desc", options.getValue().sort());
    }
}
