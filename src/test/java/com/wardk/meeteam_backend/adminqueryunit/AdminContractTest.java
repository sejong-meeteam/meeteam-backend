package com.wardk.meeteam_backend.adminqueryunit;

import com.wardk.meeteam_backend.domain.admin.dto.AdminQueryOptions;
import com.wardk.meeteam_backend.domain.admin.mapper.AdminResponseMapper;
import com.wardk.meeteam_backend.domain.admin.repository.AdminQueryRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.admin.dto.response.AdminResponses.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminContractTest {
    @Test void normalizesPagingAndEscapesLiteralSearch() {
        var defaults = AdminQueryOptions.of(null, null, "  Ab%_!  ", null);
        assertThat(defaults.page()).isZero();
        assertThat(defaults.size()).isEqualTo(10);
        assertThat(defaults.keywordPattern()).isEqualTo("%ab!%!_!!%");
        assertThat(AdminQueryOptions.of("2", "999999999999999999999", null, null).size()).isEqualTo(50);
        assertThat(AdminQueryOptions.of("0", "-3", null, null).size()).isEqualTo(1);
        assertThat(AdminQueryOptions.of("0", "0", null, null).size()).isEqualTo(10);
        assertThat(AdminQueryOptions.of("0", "bad", null, null).size()).isEqualTo(10);
    }
    @Test void rejectsInvalidPagingAndLongKeyword() {
        for (String page : List.of("-1", "bad", "2147483647")) {
            assertThatThrownBy(() -> AdminQueryOptions.of(page, "50", null, null))
                    .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_INVALID_REQUEST));
        }
        assertThatThrownBy(() -> AdminQueryOptions.of(null, null, "x".repeat(51), null)).isInstanceOf(CustomException.class);
    }
    @Test void masksOnlyStudentIdSuffix() {
        assertThat(AdminResponseMapper.maskStudentId("20261234")).isEqualTo("2026****");
        assertThat(AdminResponseMapper.maskStudentId("123")).isEqualTo("123");
        assertThat(AdminResponseMapper.maskStudentId("")).isNull();
        assertThat(AdminResponseMapper.maskStudentId(null)).isNull();
    }
    @Test void mapsOnlyRealStateAndSerializesFlatDetailWithNulls() throws Exception {
        var member = Member.createForTest("person@example.com", "Person");
        var mapper = new AdminResponseMapper(mock(S3ImageUrlResolver.class));
        var row = new AdminQueryRepository.MemberRow(member, 0);
        var summary = mapper.member(row, null, true);
        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.lastLoginAt()).isNull();
        member.withdraw();
        assertThat(mapper.member(row, null, false).status()).isEqualTo("WITHDRAWN");
        var detail = new MemberDetail(summary, null, null, null, null, null, List.of(), List.of(), null, List.of(), List.of());
        var json = new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(detail);
        assertThat(json.has("summary")).isFalse();
        assertThat(json.get("name").asText()).isEqualTo("Person");
        assertThat(json.has("lastLoginAt")).isTrue();
        assertThat(json.get("lastLoginAt").isNull()).isTrue();
        assertThat(json.has("suspendReason")).isTrue();
    }
    @Test void pageMetadataHandlesEmptyAndOutOfRangePages() {
        var page = Page.of(List.of(), 4, 10, 3);
        assertThat(page.empty()).isTrue();
        assertThat(page.last()).isTrue();
        assertThat(page.first()).isFalse();
        assertThat(page.totalElements()).isEqualTo(3);
    }
    @Test void orphanProjectDoesNotEmitAnInvalidFrontendIdentity() {
        var mapper = new AdminResponseMapper(mock(S3ImageUrlResolver.class));
        var project = com.wardk.meeteam_backend.domain.project.entity.Project.builder().name("Orphan").build();
        assertThatThrownBy(() -> mapper.project(new AdminQueryRepository.ProjectRow(project, 0, 1, 0)))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ADMIN_INVALID_STATE));
    }
}
