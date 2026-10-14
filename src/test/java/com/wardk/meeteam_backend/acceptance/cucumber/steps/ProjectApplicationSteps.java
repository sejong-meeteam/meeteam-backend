package com.wardk.meeteam_backend.acceptance.cucumber.steps;

import com.wardk.meeteam_backend.acceptance.cucumber.support.TestApiSupport;
import com.wardk.meeteam_backend.acceptance.cucumber.support.TestContext;
import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만약;
import io.cucumber.java.ko.먼저;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.wardk.meeteam_backend.acceptance.cucumber.steps.constant.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로젝트 지원 관련 Step 정의
 * 모든 데이터는 프로덕션 API를 통해 생성됩니다.
 */
public class ProjectApplicationSteps {

    @Autowired
    private TestContext context;

    @Autowired
    private TestApiSupport api;

    // ==================== Given Steps ====================

    @먼저("{string}이 로그인한 상태이다")
    public void name이_로그인한_상태이다(String name) {
        // 회원가입 (이미 가입되어 있으면 무시)
        api.auth().일반회원가입_요청(
                toEmail(name), 기본_비밀번호, name, "1998-03-15", "남성",
                List.of(JobPositionCode.JAVA_SPRING)
        );

        // 로그인
        var response = api.auth().로그인_요청(toEmail(name), 기본_비밀번호);
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);

        // Context에 토큰 저장
        String token = response.header("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            context.members().putToken(name, token.substring(7));
        }

        // Context에 memberId 저장
        Long memberId = response.jsonPath().getLong("result.memberId");
        context.members().putMemberId(name, memberId);
    }

    @먼저("{string}이 다음 포지션을 모집하는 프로젝트를 등록했다")
    public void name이_다음_포지션을_모집하는_프로젝트를_등록했다(String leaderName, DataTable dataTable) {
        // 모집 포지션 목록 파싱
        List<Map<String, Object>> recruitments = new ArrayList<>();
        for (Map<String, String> row : dataTable.asMaps()) {
            String jobField = row.get("직군");
            String position = row.get("포지션");
            int count = parseRecruitmentCount(row.get("모집인원"));
            recruitments.add(api.projectCreate().모집포지션_생성(jobField, position, count));
        }

        // 프로젝트 생성 (Context에서 토큰 사용)
        프로젝트_생성(leaderName, "테스트 프로젝트", recruitments);
    }

    @먼저("{string}이 {string} 포지션 {int}명을 모집하는 프로젝트를 등록했다")
    public void name이_position_count명을_모집하는_프로젝트를_등록했다(String leaderName, String positionString, int count) {
        JobFieldCode fieldCode = api.projectCreate().직군_문자열_파싱(positionString);
        JobPositionCode positionCode = api.projectCreate().포지션_문자열_파싱(positionString);

        List<Map<String, Object>> recruitments = List.of(
                api.projectCreate().모집포지션_생성_직접(fieldCode, positionCode, count)
        );

        // 프로젝트 생성 (Context에서 토큰 사용)
        프로젝트_생성(leaderName, "테스트 프로젝트", recruitments);
    }

    @먼저("{string}이 {string} 포지션만 모집하는 프로젝트를 등록했다")
    public void name이_position만_모집하는_프로젝트를_등록했다(String leaderName, String positionString) {
        name이_position_count명을_모집하는_프로젝트를_등록했다(leaderName, positionString, 2);
    }

    @먼저("{string}이 {string} 포지션 {int}명만 모집하는 프로젝트를 등록했다")
    public void name이_position_count명만_모집하는_프로젝트를_등록했다(String leaderName, String positionString, int count) {
        name이_position_count명을_모집하는_프로젝트를_등록했다(leaderName, positionString, count);
    }

    @그리고("{string}가 {string} 포지션에 지원한 상태이다")
    public void name가_position에_지원한_상태이다(String memberName, String positionString) {
        String token = context.members().getToken(memberName);
        JobPositionCode positionCode = api.projectCreate().포지션_문자열_파싱(positionString);

        // 지원 API 호출
        var response = api.projectApplication().지원(token, context.project().getId(), positionCode);
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);

        Long applicationId = response.jsonPath().getLong("result.applicationId");
        context.members().putApplicationId(memberName, applicationId);
    }

    @그리고("{string}가 이미 {string} 포지션에 지원한 상태이다")
    public void name가_이미_position에_지원한_상태이다(String memberName, String positionString) {
        name가_position에_지원한_상태이다(memberName, positionString);
    }

    @그리고("{string}가 {string} 포지션에 지원하여 승인된 상태이다")
    public void name가_position에_지원하여_승인된_상태이다(String memberName, String positionString) {
        // 1. 지원
        name가_position에_지원한_상태이다(memberName, positionString);

        // 2. 승인 (리더 토큰으로)
        Long applicationId = context.members().getApplicationId(memberName);
        String leaderToken = findLeaderToken();

        var response = api.projectApplication().승인(leaderToken, context.project().getId(), applicationId);
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);
    }

    @그리고("{string}는 이미 {string} 팀원으로 참여 중이다")
    public void name는_이미_position_팀원으로_참여_중이다(String memberName, String positionString) {
        // 지원 후 승인 = 팀원
        name가_position에_지원하여_승인된_상태이다(memberName, positionString);
    }

    @그리고("리더 {string}이 프로젝트 모집을 마감한 상태이다")
    public void 리더_name이_프로젝트_모집을_마감한_상태이다(String leaderName) {
        String leaderToken = context.members().getToken(leaderName);

        // 모집 상태 토글 (모집중 → 모집마감)
        var response = api.projectManagement().모집_상태_토글(leaderToken, context.project().getId());
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);

        // 모집 마감 상태 확인
        boolean isRecruiting = response.jsonPath().getBoolean("result.isRecruiting");
        assertThat(isRecruiting)
                .overridingErrorMessage("프로젝트가 모집 마감 상태여야 합니다.")
                .isFalse();
    }

    // ==================== When Steps ====================

    @만약("{string}가 {string} 포지션에 지원하면")
    public void name가_position에_지원하면(String memberName, String positionString) {
        String token = context.members().getToken(memberName);
        JobPositionCode positionCode = api.projectCreate().포지션_문자열_파싱(positionString);

        var response = api.projectApplication().지원(token, context.project().getId(), positionCode);
        context.setResponse(response);

        // Context에 applicationId 저장
        if (response.statusCode() == HTTP_OK) {
            Long applicationId = response.jsonPath().getLong("result.applicationId");
            context.members().putApplicationId(memberName, applicationId);
            context.application().setId(applicationId);
        }
    }

    @만약("리더 {string}이 자신의 프로젝트에 지원하면")
    public void 리더_name이_자신의_프로젝트에_지원하면(String leaderName) {
        String token = context.members().getToken(leaderName);

        var response = api.projectApplication().지원(token, context.project().getId(), JobPositionCode.JAVA_SPRING);
        context.setResponse(response);
    }

    @만약("{string}가 같은 프로젝트에 다시 지원하면")
    public void name가_같은_프로젝트에_다시_지원하면(String memberName) {
        String token = context.members().getToken(memberName);

        var response = api.projectApplication().지원(token, context.project().getId(), JobPositionCode.JAVA_SPRING);
        context.setResponse(response);
    }

    @만약("{string}가 {string} 포지션에 추가로 지원하면")
    public void name가_position에_추가로_지원하면(String memberName, String positionString) {
        name가_position에_지원하면(memberName, positionString);
    }

    @만약("리더 {string}이 {string}의 지원을 승인하면")
    public void 리더_name이_applicant의_지원을_승인하면(String leaderName, String applicantName) {
        String leaderToken = context.members().getToken(leaderName);
        Long applicationId = context.members().getApplicationId(applicantName);

        var response = api.projectApplication().승인(leaderToken, context.project().getId(), applicationId);
        context.setResponse(response);
    }

    @만약("리더 {string}이 {string}의 지원을 거절하면")
    public void 리더_name이_applicant의_지원을_거절하면(String leaderName, String applicantName) {
        String leaderToken = context.members().getToken(leaderName);
        Long applicationId = context.members().getApplicationId(applicantName);

        var response = api.projectApplication().거절(leaderToken, context.project().getId(), applicationId);
        context.setResponse(response);
    }

    @만약("{string}가 해당 프로젝트에 지원하면")
    public void name가_해당_프로젝트에_지원하면(String memberName) {
        String token = context.members().getToken(memberName);

        var response = api.projectApplication().지원(token, context.project().getId(), JobPositionCode.JAVA_SPRING);
        context.setResponse(response);
    }

    @만약("리더 {string}이 프로젝트 모집을 마감하면")
    public void 리더_name이_프로젝트_모집을_마감하면(String leaderName) {
        String leaderToken = context.members().getToken(leaderName);

        var response = api.projectManagement().모집_상태_토글(leaderToken, context.project().getId());
        context.setResponse(response);
    }

    // ==================== Then Steps ====================

    @그러면("지원이 정상적으로 접수된다")
    public void 지원이_정상적으로_접수된다() {
        ExtractableResponse<Response> response = context.getResponse();
        if (response.statusCode() != HTTP_OK) {
            System.out.println("지원 실패 응답: " + response.body().asString());
        }
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);
    }

    @그러면("지원이 거부된다")
    public void 지원이_거부된다() {
        assertThat(context.getResponse().statusCode()).isGreaterThanOrEqualTo(HTTP_BAD_REQUEST);
    }

    @그러면("승인이 거부된다")
    public void 승인이_거부된다() {
        assertThat(context.getResponse().statusCode()).isGreaterThanOrEqualTo(HTTP_BAD_REQUEST);
    }

    @그리고("{string}의 지원 상태는 {string}이다")
    public void name의_지원_상태는_status이다(String memberName, String expectedStatus) {
        String leaderToken = findLeaderToken();
        Long projectId = context.project().getId();
        Long applicationId = context.members().getApplicationId(memberName);

        var response = api.projectApplication().지원_상세_조회(leaderToken, projectId, applicationId);
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);

        String actualStatus = response.jsonPath().getString("result.status");
        assertThat(actualStatus).isEqualTo(expectedStatus);
    }



    @그리고("프로젝트 리더 {string}에게 {string}의 지원 알림이 발송된다")
    public void 프로젝트_리더에게_지원_알림이_발송된다(String leaderName, String applicantName) {
        String token = context.members().getToken(leaderName);
        Long applicationId = context.members().getApplicationId(applicantName);

        알림_검증(token, applicationId, "PROJECT_APPLY",
                "리더 %s에게 %s의 지원 알림이 발송되지 않았습니다.".formatted(leaderName, applicantName));
    }

    @그리고("{string}가 프로젝트의 팀원으로 등록된다")
    public void name가_프로젝트의_팀원으로_등록된다(String memberName) {
        Long memberId = context.members().getMemberId(memberName);
        Long projectId = context.project().getId();
        String token = findLeaderToken();

        var response = api.projectManagement().팀원_목록_조회(token, projectId);
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);

        List<Long> memberIds = response.jsonPath().getList("result.memberId", Long.class);

        assertThat(memberIds)
                .overridingErrorMessage("%s가 프로젝트 %d의 팀원으로 등록되지 않았습니다.", memberName, projectId)
                .contains(memberId);
    }

    @그리고("{string} 포지션의 현재 인원은 {int}명이다")
    public void position_포지션의_현재_인원은_count명이다(String positionString, int expectedCount) {
        Long projectId = context.project().getId();
        // "프론트 - 웹 프론트엔드" 형식에서 포지션명만 추출
        String[] parts = positionString.split(" - ");
        String positionName = parts.length > 1 ? parts[1].trim() : parts[0].trim();

        var response = api.projectCreate().상세조회(projectId);
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);

        List<Map<String, Object>> recruitments = response.jsonPath().getList("result.recruitments");

        int currentCount = recruitments.stream()
                .filter(r -> positionName.equals(r.get("jobPositionName")))
                .map(r -> (Integer) r.get("currentCount"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("모집 포지션을 찾을 수 없습니다: " + positionString));

        assertThat(currentCount)
                .overridingErrorMessage("%s 포지션의 현재 인원이 %d명이어야 합니다. 실제: %d명", positionString, expectedCount, currentCount)
                .isEqualTo(expectedCount);
    }

    @그리고("{string}에게 지원 승인 알림이 발송된다")
    public void name에게_지원_승인_알림이_발송된다(String memberName) {
        String token = context.members().getToken(memberName);
        Long applicationId = context.members().getApplicationId(memberName);

        알림_검증(token, applicationId, "PROJECT_APPROVE",
                "%s에게 승인 알림이 발송되지 않았습니다.".formatted(memberName));
    }

    @그리고("{string}에게 지원 거절 알림이 발송된다")
    public void name에게_지원_거절_알림이_발송된다(String memberName) {
        String token = context.members().getToken(memberName);
        Long applicationId = context.members().getApplicationId(memberName);

        알림_검증(token, applicationId, "PROJECT_REJECT",
                "%s에게 거절 알림이 발송되지 않았습니다.".formatted(memberName));
    }

    @그리고("{string} 안내를 받는다")
    public void message_안내를_받는다(String expectedMessage) {
        ExtractableResponse<Response> response = context.getResponse();
        String actualMessage = response.jsonPath().getString("message");

        if (actualMessage == null) {
            actualMessage = response.body().asString();
        }

        String keyword = mapExpectedMessage(expectedMessage);

        assertThat(actualMessage.toLowerCase())
                .overridingErrorMessage("기대 키워드 [%s]를 포함하지 않습니다. 실제: [%s]", keyword, actualMessage)
                .contains(keyword.toLowerCase());
    }

    @그러면("프로젝트의 모집 상태가 {string}으로 변경된다")
    public void 프로젝트의_모집_상태가_status로_변경된다(String expectedStatus) {
        Long projectId = context.project().getId();

        var response = api.projectCreate().상세조회(projectId);
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);

        boolean isRecruiting = response.jsonPath().getBoolean("result.isRecruiting");

        if ("모집마감".equals(expectedStatus)) {
            assertThat(isRecruiting)
                    .overridingErrorMessage("프로젝트 모집 상태가 '모집마감'이어야 합니다. 현재: 모집중")
                    .isFalse();
        } else if ("모집중".equals(expectedStatus)) {
            assertThat(isRecruiting)
                    .overridingErrorMessage("프로젝트 모집 상태가 '모집중'이어야 합니다. 현재: 모집마감")
                    .isTrue();
        }
    }

    @그리고("모든 포지션의 모집 상태가 {string}이다")
    public void 모든_포지션의_모집_상태가_status이다(String expectedStatus) {
        Long projectId = context.project().getId();

        var response = api.projectCreate().상세조회(projectId);
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);

        List<Map<String, Object>> recruitments = response.jsonPath().getList("result.recruitments");

        boolean allClosed = recruitments.stream()
                .allMatch(r -> Boolean.TRUE.equals(r.get("isClosed")));

        if ("마감".equals(expectedStatus)) {
            assertThat(allClosed)
                    .overridingErrorMessage("모든 포지션이 마감 상태여야 합니다.")
                    .isTrue();
        }
    }

    // ==================== Helper Methods ====================

    private String toEmail(String name) {
        return switch (name) {
            case "홍길동" -> "hong@example.com";
            case "김철수" -> "kim@example.com";
            case "박영희" -> "park@example.com";
            case "이민수" -> "lee@example.com";
            default -> name.toLowerCase().replaceAll(" ", "") + "@example.com";
        };
    }

    private int parseRecruitmentCount(String countStr) {
        if (countStr == null || countStr.isBlank()) {
            return 1;
        }
        return Integer.parseInt(countStr.replaceAll("[^0-9]", ""));
    }

    /**
     * 프로젝트 생성 공통 메서드
     */
    private void 프로젝트_생성(String leaderName, String projectName, List<Map<String, Object>> recruitments) {
        String token = context.members().getToken(leaderName);

        var response = api.projectCreate().생성_여러_포지션(token, projectName, "CAPSTONE", recruitments);
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);

        Long projectId = response.jsonPath().getLong("result.id");
        context.project().setId(projectId);
        context.project().setName(projectName);
        context.project().setLeaderName(leaderName);
    }

    private String findLeaderToken() {
        String leaderName = context.project().getLeaderName();
        return context.members().getToken(leaderName);
    }

    /**
     * 알림 검증 공통 메서드
     * applicationId와 type이 일치하는 알림이 존재하는지 확인
     */
    private void 알림_검증(String token, Long applicationId, String expectedType, String errorMessage) {
        var response = api.notification().조회(token);
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);

        List<Map<String, Object>> notifications = response.jsonPath().getList("result.content");

        boolean found = notifications.stream()
                .anyMatch(n -> applicationId.equals(((Number) n.get("applicationId")).longValue())
                        && expectedType.equals(n.get("type")));

        assertThat(found)
                .overridingErrorMessage(errorMessage + " (applicationId=%d, type=%s)", applicationId, expectedType)
                .isTrue();
    }

    private String mapExpectedMessage(String expectedMessage) {
        return switch (expectedMessage) {
            case "해당 포지션은 현재 모집하고 있지 않습니다" -> "모집";
            case "자신의 프로젝트에는 지원할 수 없습니다." -> "자신";
            case "이미 해당 프로젝트에 지원하셨습니다" -> "이미";
            case "이미 해당 프로젝트에 참여 중입니다" -> "이미";
            case "해당 포지션은 모집이 마감되었습니다" -> "마감";
            case "해당 포지션의 모집 인원이 모두 찼습니다" -> "모집";
            case "해당 프로젝트는 모집이 마감되었습니다" -> "마감";
            default -> expectedMessage;
        };
    }
}