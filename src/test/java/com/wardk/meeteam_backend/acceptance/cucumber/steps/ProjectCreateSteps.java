package com.wardk.meeteam_backend.acceptance.cucumber.steps;

import com.wardk.meeteam_backend.acceptance.cucumber.factory.MemberFactory;
import com.wardk.meeteam_backend.acceptance.cucumber.support.TestApiSupport;
import com.wardk.meeteam_backend.acceptance.cucumber.support.TestContext;
import com.wardk.meeteam_backend.acceptance.cucumber.support.TestRepositorySupport;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMemberRole;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만약;
import io.cucumber.java.ko.먼저;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.wardk.meeteam_backend.acceptance.cucumber.steps.constant.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로젝트 생성 관련 Step 정의
 */
public class ProjectCreateSteps {

    @Autowired
    private TestContext context;

    @Autowired
    private TestApiSupport api;

    @Autowired
    private TestRepositorySupport repository;

    @Autowired
    private MemberFactory memberFactory;

    // ==================== Given Steps ====================

    @먼저("로그인하지 않은 상태이다")
    public void 로그인하지_않은_상태이다() {
        context.setAccessToken(null);
    }

    // ==================== When Steps ====================

    @만약("다음 조건으로 프로젝트를 등록하면:")
    public void 다음_조건으로_프로젝트를_등록하면(DataTable dataTable) {
        List<Map<String, String>> rows = dataTable.asMaps();

        // 첫 번째 행에서 프로젝트 기본 정보 추출
        Map<String, String> firstRow = rows.get(0);
        String projectName = firstRow.get("프로젝트명");
        String category = firstRow.get("카테고리");

        // 모집 포지션 목록 생성
        List<Map<String, Object>> recruitments = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String jobFieldCode = row.get("모집직군");
            if (jobFieldCode != null && !jobFieldCode.isBlank()) {
                int recruitmentCount = parseRecruitmentCount(row.get("모집인원"));
                recruitments.add(api.projectCreate().모집포지션_생성(jobFieldCode, recruitmentCount));
            }
        }

        var response = api.projectCreate().생성_여러_포지션(
                context.getAccessToken(),
                projectName,
                category,
                recruitments
        );
        context.setResponse(response);

        // 성공 시 프로젝트 ID 저장
        if (response.statusCode() == HTTP_OK) {
            Long projectId = response.jsonPath().getLong("result.id");
            context.project().setId(projectId);
            context.project().setName(projectName);
        }
    }

    @만약("마감 방식을 {string}로 설정하고 프로젝트를 등록하면")
    public void 마감_방식을_설정하고_프로젝트를_등록하면(String deadlineType) {
        LocalDate endDate = "END_DATE".equals(deadlineType) ? LocalDate.now().plusMonths(1) : null;
        var response = api.projectCreate().생성_마감방식(context.getAccessToken(), deadlineType, endDate);
        context.setResponse(response);

        if (response.statusCode() == HTTP_OK) {
            Long projectId = response.jsonPath().getLong("result.id");
            context.project().setId(projectId);
        }
    }

    @만약("마감 방식을 {string}로 하고 마감일을 지정하여 프로젝트를 등록하면")
    public void 마감_방식을_하고_마감일을_지정하여_프로젝트를_등록하면(String deadlineType) {
        var response = api.projectCreate().생성_마감방식(
                context.getAccessToken(),
                deadlineType,
                LocalDate.now().plusMonths(1)
        );
        context.setResponse(response);

        if (response.statusCode() == HTTP_OK) {
            Long projectId = response.jsonPath().getLong("result.id");
            context.project().setId(projectId);
        }
    }

    @만약("프로젝트명 없이 등록을 요청하면")
    public void 프로젝트명_없이_등록을_요청하면() {
        var response = api.projectCreate().프로젝트명_누락_요청(context.getAccessToken());
        context.setResponse(response);
    }

    @만약("모집 포지션 없이 등록을 요청하면")
    public void 모집_포지션_없이_등록을_요청하면() {
        var response = api.projectCreate().모집포지션_누락_요청(context.getAccessToken());
        context.setResponse(response);
    }

    @만약("마감 방식 없이 프로젝트 등록을 요청하면")
    public void 마감_방식_없이_프로젝트_등록을_요청하면() {
        var response = api.projectCreate().마감방식_누락_요청(context.getAccessToken());
        context.setResponse(response);
    }

    @만약("마감 방식을 {string}로 하고 마감일 없이 등록을 요청하면")
    public void 마감_방식을_하고_마감일_없이_등록을_요청하면(String deadlineType) {
        var response = api.projectCreate().마감일_누락_요청(context.getAccessToken());
        context.setResponse(response);
    }

    @만약("마감 방식을 {string}로 하고 마감일을 함께 등록을 요청하면")
    public void 마감_방식을_하고_마감일을_함께_등록을_요청하면(String deadlineType) {
        var response = api.projectCreate().모집완료_마감일_포함_요청(context.getAccessToken());
        context.setResponse(response);
    }

    @만약("프로젝트 등록을 요청하면")
    public void 프로젝트_등록을_요청하면() {
        var response = api.projectCreate().비인증_생성_요청();
        context.setResponse(response);
    }

    // ==================== Then Steps ====================

    @그러면("프로젝트 등록에 성공한다")
    public void 프로젝트_등록에_성공한다() {
        ExtractableResponse<Response> response = context.getResponse();
        if (response.statusCode() != HTTP_OK) {
            System.out.println("프로젝트 등록 실패 응답: " + response.body().asString());
        }
        assertThat(response.statusCode()).isEqualTo(HTTP_OK);
    }

    @그러면("프로젝트 등록에 실패한다")
    public void 프로젝트_등록에_실패한다() {
        assertThat(context.getResponse().statusCode()).isGreaterThanOrEqualTo(HTTP_BAD_REQUEST);
    }

    @그리고("{string}이 프로젝트 리더로 지정된다")
    public void 이_프로젝트_리더로_지정된다(String memberName) {
        Long projectId = context.project().getId();
        assertThat(projectId).isNotNull();

        // 프로젝트 멤버 조회하여 리더 확인
        var projectMembers = repository.projectMember().findAllByProjectIdWithMember(projectId);
        boolean hasLeader = projectMembers.stream()
                .anyMatch(pm -> pm.getRole() == ProjectMemberRole.LEADER
                        && pm.getMember().getRealName().equals(memberName));

        assertThat(hasLeader)
                .overridingErrorMessage("프로젝트 ID %d에서 %s이 리더로 지정되지 않았습니다.", projectId, memberName)
                .isTrue();
    }

    @그리고("프로젝트 상세에서 {string} 표시를 확인할 수 있다")
    public void 프로젝트_상세에서_표시를_확인할_수_있다(String expectedText) {
        Long projectId = context.project().getId();
        var response = api.projectCreate().상세조회(projectId);

        assertThat(response.statusCode()).isEqualTo(HTTP_OK);
        // 마감 방식 확인
        String deadlineType = response.jsonPath().getString("result.recruitmentDeadlineType");
        if ("모집 완료 시까지".equals(expectedText)) {
            assertThat(deadlineType).isEqualTo("RECRUITMENT_COMPLETED");
        }
    }

    // ==================== Helper Methods ====================

    private int parseRecruitmentCount(String countStr) {
        if (countStr == null || countStr.isBlank()) {
            return 1;
        }
        // "2명" -> 2, "2" -> 2
        return Integer.parseInt(countStr.replaceAll("[^0-9]", ""));
    }
}
