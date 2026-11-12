package com.wardk.meeteam_backend.nameprivacyunit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMemberRole;
import com.wardk.meeteam_backend.fixture.MemberFixture;
import com.wardk.meeteam_backend.fixture.ProjectFixture;
import com.wardk.meeteam_backend.web.mainpage.dto.response.MainPageProjectResponse;
import com.wardk.meeteam_backend.web.mainpage.dto.response.MemberCardResponse;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import com.wardk.meeteam_backend.web.member.dto.response.MemberDetailResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectDetailResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectListResponse;
import com.wardk.meeteam_backend.web.project.dto.response.ProjectResponse;
import com.wardk.meeteam_backend.web.projectlike.dto.response.ProjectWithLikeDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicNameResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void memberCardAndDetailExposePublicNameWithoutRawNameField() throws Exception {
        Member privateMember = privateMember("비공개사용자");

        assertPrivateName(MemberCardResponse.from(privateMember), privateMember);
        assertPrivateName(MemberDetailResponse.from(privateMember, List.of()), privateMember);
    }

    @Test
    void publicMemberCardKeepsRealNameVisibleByDefault() throws Exception {
        Member publicMember = MemberFixture.withName("공개사용자");

        JsonNode json = json(MemberCardResponse.from(publicMember));

        assertThat(json.path("name").asText()).isEqualTo("공개사용자");
        assertThat(json.toString()).doesNotContain("realName");
    }

    @Test
    void projectCreatorResponsesDoNotLeakPrivateCreatorName() throws Exception {
        Member privateCreator = privateMember("비공개리더");
        Project project = ProjectFixture.defaultProject(privateCreator);

        assertPrivateCreatorName(MainPageProjectResponse.responseDto(project), privateCreator);
        assertPrivateCreatorName(ProjectCardResponse.from(project, List.of(), false), privateCreator);
        assertPrivateCreatorName(ProjectListResponse.responseDto(project), privateCreator);
    }

    @Test
    void projectAndTeamResponsesMaskPrivateNamesEvenForLeaderViewer() throws Exception {
        Member privateLeader = privateMember("비공개리더");
        Member privateTeammate = privateMember("비공개팀원");
        Project project = ProjectFixture.defaultProject(privateLeader);
        project.joinMember(ProjectMember.createProjectMember(privateLeader, null, ProjectMemberRole.LEADER));
        project.joinMember(ProjectMember.createProjectMember(privateTeammate, null, ProjectMemberRole.MEMBER));

        JsonNode detail = json(ProjectDetailResponse.from(project, false, true));
        assertThat(detail.path("leader").path("name").asText()).isEqualTo(privateLeader.getPublicName());
        assertThat(detail.path("members").get(0).path("name").asText()).isEqualTo(privateLeader.getPublicName());
        assertThat(detail.path("members").get(1).path("name").asText()).isEqualTo(privateTeammate.getPublicName());
        assertThat(detail.toString()).doesNotContain("비공개리더", "비공개팀원", "realName");

        assertTeamNames(ProjectResponse.responseDto(project), privateLeader, privateTeammate);
        assertTeamNames(new ProjectWithLikeDto(project, 0), privateLeader, privateTeammate);
    }

    private Member privateMember(String realName) {
        Member member = MemberFixture.withName(realName);
        ReflectionTestUtils.setField(member, "id", Math.abs((long) realName.hashCode()));
        // A display-name policy does not redact user-authored email text. Use an
        // independent synthetic address so the JSON assertion isolates name fields.
        ReflectionTestUtils.setField(member, "email", "synthetic-" + Math.abs((long) realName.hashCode()) + "@example.invalid");
        ReflectionTestUtils.setField(member, "namePrivate", true);
        return member;
    }

    private void assertPrivateName(Object response, Member member) throws Exception {
        JsonNode json = json(response);
        assertThat(json.path("name").asText()).isEqualTo(member.getPublicName());
        assertThat(json.toString()).doesNotContain(member.getRealName(), "realName");
    }

    private void assertPrivateCreatorName(Object response, Member member) throws Exception {
        JsonNode json = json(response);
        assertThat(json.path("creatorName").asText()).isEqualTo(member.getPublicName());
        assertThat(json.toString()).doesNotContain(member.getRealName(), "realName");
    }

    private void assertTeamNames(Object response, Member leader, Member teammate) throws Exception {
        JsonNode members = json(response).path("projectMembers");
        assertThat(members.get(0).path("name").asText()).isEqualTo(leader.getPublicName());
        assertThat(members.get(1).path("name").asText()).isEqualTo(teammate.getPublicName());
        assertThat(members.toString()).doesNotContain(leader.getRealName(), teammate.getRealName(), "realName");
    }

    private JsonNode json(Object response) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(response));
    }
}
