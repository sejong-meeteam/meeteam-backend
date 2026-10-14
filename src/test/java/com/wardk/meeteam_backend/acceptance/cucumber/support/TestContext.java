package com.wardk.meeteam_backend.acceptance.cucumber.support;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/**
 * 시나리오 내 Step 클래스 간 데이터 공유를 위한 중앙 저장소
 * @ScenarioScope로 시나리오마다 새로운 인스턴스 생성
 */
@Component
@ScenarioScope
public class TestContext {

    // === 공통 필드 ===
    private String accessToken;
    private ExtractableResponse<Response> response;
    private String lastMessage;

    // === 도메인별 데이터 클래스 ===
    @Getter @Setter
    public static class MemberData {
        private Long id;
        private String email;
        private String name;
        private String password;
        private String oauthCode;  // OAuth 회원가입용 코드
    }

    @Getter @Setter
    public static class ProjectData {
        private Long id;
        private String name;
        private String leaderName;
    }

    @Getter @Setter
    public static class ApplicationData {
        private Long id;
        private Long projectId;
        private Long applicantId;
        private String status;
        private String jobFieldCode;
        private String jobPositionCode;
    }

    @Getter @Setter
    public static class MultiMemberData {
        private java.util.Map<String, Long> memberIds = new java.util.HashMap<>();
        private java.util.Map<String, String> memberTokens = new java.util.HashMap<>();
        private java.util.Map<String, Long> applicationIds = new java.util.HashMap<>();

        public void putMemberId(String name, Long id) {
            memberIds.put(name, id);
        }

        public Long getMemberId(String name) {
            return memberIds.get(name);
        }

        public void putToken(String name, String token) {
            memberTokens.put(name, token);
        }

        public String getToken(String name) {
            return memberTokens.get(name);
        }

        public void putApplicationId(String name, Long applicationId) {
            applicationIds.put(name, applicationId);
        }

        public Long getApplicationId(String name) {
            return applicationIds.get(name);
        }
    }

    // === 도메인 데이터 인스턴스 ===
    private final MemberData memberData = new MemberData();
    private final ProjectData projectData = new ProjectData();
    private final ApplicationData applicationData = new ApplicationData();
    private final MultiMemberData multiMemberData = new MultiMemberData();

    // === 공통 필드 Getter/Setter ===
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public ExtractableResponse<Response> getResponse() {
        return response;
    }

    public void setResponse(ExtractableResponse<Response> response) {
        this.response = response;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    // === 도메인 데이터 접근 메서드 ===
    public MemberData member() {
        return memberData;
    }

    public ProjectData project() {
        return projectData;
    }

    public ApplicationData application() {
        return applicationData;
    }

    public MultiMemberData members() {
        return multiMemberData;
    }

}