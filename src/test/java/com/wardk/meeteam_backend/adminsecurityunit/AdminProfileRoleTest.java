package com.wardk.meeteam_backend.adminsecurityunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.UserRole;
import com.wardk.meeteam_backend.global.response.ErrorResponse;
import com.wardk.meeteam_backend.web.member.dto.response.MemberProfileResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class AdminProfileRoleTest {
    @ParameterizedTest
    @EnumSource(UserRole.class)
    void profileExposesActualRole(UserRole role) throws Exception {
        Member member = Member.createForTest("unit@example.invalid", "테스트 회원");
        ReflectionTestUtils.setField(member, "role", role);
        var profile = new MemberProfileResponse(member, 1L);
        assertEquals(role, profile.getRole());
        assertEquals(role.name(), new ObjectMapper().valueToTree(profile).get("role").asText());
    }

    @Test
    void failureEnvelopeHasExplicitNullResultEvenWithNonNullMapper() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        var json = mapper.valueToTree(ErrorResponse.getResponse("ADMIN403", "관리자 권한이 필요합니다."));
        assertTrue(json.has("result"));
        assertTrue(json.get("result").isNull());
        assertFalse(json.has("password"));
    }
}
