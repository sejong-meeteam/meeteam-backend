package com.wardk.meeteam_backend.projectvalidationunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.project.service.*;
import com.wardk.meeteam_backend.global.exception.handler.GlobalExceptionHandler;
import com.wardk.meeteam_backend.web.project.controller.ProjectController;
import com.wardk.meeteam_backend.web.project.controller.ProjectManagementController;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectEditRequest;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectPostRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Synthetic DTO/HTTP requests only. No DB, uploaded images or external services. */
class CommunicationChannelValidationTest {
    private static final String MESSAGE = "올바른 Discord·Slack·카카오 오픈채팅 링크를 입력해 주세요.";
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

    private static Stream<Arguments> urls(String... urls) {
        return requestTypes().flatMap(type -> Stream.of(urls).map(url -> Arguments.of(type, url)));
    }

    static Stream<Arguments> allowedLinks() {
        return urls("https://discord.gg/Abc123", "https://discord.com/invite/Abc123/",
                "https://join.slack.com/t/example/shared_invite/zt-Abc_123",
                "https://example-workspace.slack.com", "https://open.kakao.com/o/Abc123",
                "https://open.kakao.com/o/Abc123/");
    }

    static Stream<Arguments> invalidLinks() {
        return urls("", "not-a-url", "https://example.com", "http://discord.gg/Abc123",
                "https://discord.gg/", " https://discord.gg/Abc123",
                "https://discord.gg/Abc123?foo=bar");
    }

    @ParameterizedTest
    @MethodSource("allowedLinks")
    void existingAllowedLinkFormatsRemainAccepted(Class<?> type, String url) {
        assertTrue(validator.validateValue(type, "communicationChannelUrl", url).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidLinks")
    void invalidLinksReturnTheFriendlyMessageInsteadOfTheRegex(Class<?> type, String url) {
        var violations = validator.validateValue(type, "communicationChannelUrl", url);
        assertEquals(1, violations.size());
        assertEquals(MESSAGE, violations.iterator().next().getMessage());
    }

    @ParameterizedTest
    @MethodSource("requestTypes")
    void omittedOptionalLinkRemainsAllowed(Class<?> type) {
        assertTrue(validator.validateValue(type, "communicationChannelUrl", null).isEmpty());
    }

    @Test
    void createEndpointReturnsFriendlyMessageInTheExisting400Response() throws Exception {
        var commands = mock(ProjectCommandService.class);
        var queries = mock(ProjectQueryService.class);
        var mvc = mvc(new ProjectController(commands, queries));
        mvc.perform(multipart("/api/v1/projects").file(requestPart(false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.message").value(MESSAGE));
        verifyNoInteractions(commands, queries);
    }

    @Test
    void editEndpointReturnsFriendlyMessageBeforeAnyDataIsChanged() throws Exception {
        var management = mock(ProjectManagementService.class);
        var edits = mock(ProjectEditService.class);
        var mvc = mvc(new ProjectManagementController(management, edits));
        mvc.perform(multipart("/api/v1/projects/1").file(requestPart(true))
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.message").value(MESSAGE));
        verifyNoInteractions(management, edits);
    }

    private MockMvc mvc(Object controller) {
        return standaloneSetup(controller).setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private MockMultipartFile requestPart(boolean edit) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put(edit ? "name" : "projectName", "검증용 프로젝트");
        body.put("projectCategory", "CAPSTONE");
        body.put("platformCategory", "WEB");
        body.put(edit ? "leaderJobPositionCode" : "creatorJobPositionCode", "JAVA_SPRING");
        body.put("recruitmentDeadlineType", "RECRUITMENT_COMPLETED");
        body.put("communicationChannelUrl", "https://example.com");
        body.put("recruitments", List.of(java.util.Map.of("jobFieldCode", "BACKEND",
                "jobPositionCode", "JAVA_SPRING", "recruitmentCount", 1, "techStackIds", List.of(1))));
        return new MockMultipartFile("request", "request.json", MediaType.APPLICATION_JSON_VALUE,
                new ObjectMapper().writeValueAsBytes(body));
    }
}
