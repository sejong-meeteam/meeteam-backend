package com.wardk.meeteam_backend.nameprivacyunit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.web.mainpage.dto.response.MemberCardResponse;
import com.wardk.meeteam_backend.web.member.dto.response.MemberDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PublicMemberDepartmentTest {
    private Member member(String department, boolean namePrivate) {
        Member member = Member.createForTest("synthetic@example.invalid", "김민수");
        ReflectionTestUtils.setField(member, "id", 7L);
        ReflectionTestUtils.setField(member, "department", department);
        member.updateNamePrivacy(namePrivate);
        return member;
    }

    @Test void publicCardsAndDetailIncludeStoredDepartment() {
        Member member = member("컴퓨터공학과", false);
        assertThat(MemberCardResponse.from(member).department()).isEqualTo("컴퓨터공학과");
        assertThat(MemberDetailResponse.from(member, List.of()).getDepartment()).isEqualTo("컴퓨터공학과");
    }

    @Test void privateNamesStayMaskedWhileDepartmentRemainsVisible() {
        Member member = member("컴퓨터공학과", true);
        var mapper = new ObjectMapper().findAndRegisterModules();
        for (Object response : new Object[]{MemberCardResponse.from(member), MemberDetailResponse.from(member, List.of())}) {
            var json = mapper.valueToTree(response);
            assertThat(json.path("name").asText()).isEqualTo("김**");
            assertThat(json.path("department").asText()).isEqualTo("컴퓨터공학과");
            assertThat(json.toString()).doesNotContain("김민수", "realName");
        }
        assertThat(member.getRealName()).isEqualTo("김민수");
        assertThat(member.isNamePrivate()).isTrue();
    }

    @Test void missingDepartmentIsExplicitNullEvenWithGlobalNonNullSerialization() {
        Member member = member(null, true);
        var mapper = new ObjectMapper().findAndRegisterModules().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        for (Object response : new Object[]{MemberCardResponse.from(member), MemberDetailResponse.from(member, List.of())}) {
            var json = mapper.valueToTree(response);
            assertThat(json.has("department")).isTrue();
            assertThat(json.path("department").isNull()).isTrue();
            assertThat(json.path("name").asText()).isEqualTo("김**");
        }
    }

    @Test void listSerializationKeepsEachMembersOwnDepartmentWithoutChangingExistingFields() {
        Member first = member("컴퓨터공학과", true), second = member("경영학과", false);
        ReflectionTestUtils.setField(second, "id", 8L);
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(
                List.of(MemberCardResponse.from(first), MemberCardResponse.from(second)));
        assertThat(json.get(0).path("department").asText()).isEqualTo("컴퓨터공학과");
        assertThat(json.get(1).path("department").asText()).isEqualTo("경영학과");
        assertThat(json.get(0).path("memberId").asLong()).isEqualTo(7L);
        assertThat(json.get(1).path("name").asText()).isEqualTo("김민수");
        assertThat(json.get(0).path("projectCount").asInt()).isZero();
        assertThat(json.get(0).path("techStacks").isArray()).isTrue();
    }
}
