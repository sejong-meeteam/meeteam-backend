package com.wardk.meeteam_backend.utround1unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wardk.meeteam_backend.domain.project.entity.MeetingMode;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCollaboration;
import com.wardk.meeteam_backend.domain.project.service.ProjectEditService;
import com.wardk.meeteam_backend.domain.project.service.ProjectManagementService;
import com.wardk.meeteam_backend.global.exception.handler.GlobalExceptionHandler;
import com.wardk.meeteam_backend.web.project.controller.ProjectManagementController;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectEditRequest;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectPostRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Synthetic DTO/HTTP requests only. No DB, uploaded images or external services. */
class ProjectCollaborationRequestTest {
    private static final String HOURS_MESSAGE = "주 예상 활동 시간은 1~40시간으로 입력해 주세요.";
    private static final String NOTE_MESSAGE = "협업 방식 메모는 최대 100자까지 입력 가능합니다.";
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
    }

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    static Stream<Class<?>> requestTypes() {
        return Stream.of(ProjectPostRequest.class, ProjectEditRequest.class);
    }

    private static Stream<Arguments> each(Object... values) {
        return requestTypes().flatMap(type -> Stream.of(values).map(value -> Arguments.of(type, value)));
    }

    static Stream<Arguments> acceptedHours() { return each(1, 40, null); }
    static Stream<Arguments> rejectedHours() { return each(0, 41, -1); }
    static Stream<Arguments> acceptedNotes() { return each("가".repeat(100), null); }

    @ParameterizedTest
    @MethodSource("acceptedHours")
    void weeklyHoursWithinOneToFortyOrOmittedIsAccepted(Class<?> type, Integer hours) {
        assertTrue(validator.validateValue(type, "weeklyHours", hours).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("rejectedHours")
    void weeklyHoursOutsideOneToFortyIsRejected(Class<?> type, Integer hours) {
        var violations = validator.validateValue(type, "weeklyHours", hours);
        assertEquals(1, violations.size());
        assertEquals(HOURS_MESSAGE, violations.iterator().next().getMessage());
    }

    @ParameterizedTest
    @MethodSource("acceptedNotes")
    void meetingNoteUpToOneHundredCharactersOrOmittedIsAccepted(Class<?> type, String note) {
        assertTrue(validator.validateValue(type, "meetingNote", note).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("requestTypes")
    void meetingNoteOverOneHundredCharactersIsRejected(Class<?> type) {
        var violations = validator.validateValue(type, "meetingNote", "가".repeat(101));
        assertEquals(1, violations.size());
        assertEquals(NOTE_MESSAGE, violations.iterator().next().getMessage());
    }

    @Test
    void createJsonFieldsReachTheDomainCommand() throws Exception {
        var request = JSON.readValue(JSON.writeValueAsBytes(body(false, Map.of(
                "meetingMode", "HYBRID", "weeklyHours", 6, "meetingNote", "화요일 저녁 7시 디스코드"))),
                ProjectPostRequest.class);
        assertEquals(new ProjectCollaboration(MeetingMode.HYBRID, 6, "화요일 저녁 7시 디스코드"),
                request.toCommand().collaboration());
    }

    @Test
    void editJsonFieldsReachTheDomainCommand() throws Exception {
        var request = JSON.readValue(JSON.writeValueAsBytes(body(true, Map.of(
                "meetingMode", "ONLINE", "weeklyHours", 40, "meetingNote", "격주 토요일"))),
                ProjectEditRequest.class);
        assertEquals(new ProjectCollaboration(MeetingMode.ONLINE, 40, "격주 토요일"),
                request.toCommand().collaboration());
    }

    @Test
    void currentFrontendRequestWithoutTheFieldsStillMapsToEmptyCollaboration() throws Exception {
        var create = JSON.readValue(JSON.writeValueAsBytes(body(false, Map.of())), ProjectPostRequest.class);
        var edit = JSON.readValue(JSON.writeValueAsBytes(body(true, Map.of())), ProjectEditRequest.class);
        assertTrue(validator.validate(create).isEmpty());
        assertTrue(validator.validate(edit).isEmpty());
        assertEquals(ProjectCollaboration.EMPTY, create.toCommand().collaboration());
        assertEquals(ProjectCollaboration.EMPTY, edit.toCommand().collaboration());
    }

    @Test
    void blankMeetingNoteIsStoredAsNotEntered() {
        assertNull(new ProjectCollaboration(MeetingMode.OFFLINE, 3, "   ").getMeetingNote());
    }

    @Test
    void editEndpointRejectsOutOfRangeHoursBeforeAnyDataIsChanged() throws Exception {
        var management = mock(ProjectManagementService.class);
        var edits = mock(ProjectEditService.class);
        var mvc = standaloneSetup(new ProjectManagementController(management, edits)).setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var part = new MockMultipartFile("request", "request.json", MediaType.APPLICATION_JSON_VALUE,
                JSON.writeValueAsBytes(body(true, Map.of("weeklyHours", 41))));
        mvc.perform(multipart("/api/v1/projects/1").file(part)
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.message").value(HOURS_MESSAGE));
        verifyNoInteractions(management, edits);
    }

    private static Map<String, Object> body(boolean edit, Map<String, Object> collaboration) {
        var body = new LinkedHashMap<String, Object>();
        body.put(edit ? "name" : "projectName", "검증용 프로젝트");
        body.put("projectCategory", "STUDY");
        body.put("platformCategory", "WEB");
        body.put(edit ? "leaderJobPositionCode" : "creatorJobPositionCode", "JAVA_SPRING");
        body.put("recruitmentDeadlineType", "RECRUITMENT_COMPLETED");
        body.put("recruitments", List.of(Map.of("jobFieldCode", "BACKEND",
                "jobPositionCode", "JAVA_SPRING", "recruitmentCount", 1, "techStackIds", List.of(1))));
        body.putAll(collaboration);
        return body;
    }
}
