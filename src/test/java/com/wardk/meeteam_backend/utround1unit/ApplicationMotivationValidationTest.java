package com.wardk.meeteam_backend.utround1unit;

import com.wardk.meeteam_backend.domain.application.service.ProjectApplicationService;
import com.wardk.meeteam_backend.global.exception.handler.GlobalExceptionHandler;
import com.wardk.meeteam_backend.web.application.controller.ApplicationController;
import com.wardk.meeteam_backend.web.application.dto.request.ApplicationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Synthetic DTO/HTTP requests only. The service is mocked; no DB. */
class ApplicationMotivationValidationTest {
    private static final String LENGTH_MESSAGE = "지원 동기는 10자 이상 700자 이하로 입력해 주세요.";
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

    static Stream<String> acceptedLengths() {
        return Stream.of("가".repeat(10), "가".repeat(700));
    }

    static Stream<String> rejectedLengths() {
        return Stream.of("가".repeat(9), "가".repeat(701));
    }

    @ParameterizedTest
    @MethodSource("acceptedLengths")
    void motivationFromTenToSevenHundredCharactersIsAccepted(String motivation) {
        assertTrue(validator.validateValue(ApplicationRequest.class, "motivation", motivation).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("rejectedLengths")
    void motivationOutsideTenToSevenHundredCharactersIsRejected(String motivation) {
        var violations = validator.validateValue(ApplicationRequest.class, "motivation", motivation);
        assertEquals(1, violations.size());
        assertEquals(LENGTH_MESSAGE, violations.iterator().next().getMessage());
    }

    @Test
    void missingOrBlankMotivationIsRejected() {
        assertFalse(validator.validateValue(ApplicationRequest.class, "motivation", null).isEmpty());
        assertFalse(validator.validateValue(ApplicationRequest.class, "motivation", " ".repeat(10)).isEmpty());
    }

    @Test
    void applyEndpointRejectsShortMotivationBeforeSavingAnything() throws Exception {
        var service = mock(ProjectApplicationService.class);
        var mvc = standaloneSetup(new ApplicationController(service)).setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/projects/1/application").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPositionCode\":\"JAVA_SPRING\",\"motivation\":\"짧은 지원 동기\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.message").value(LENGTH_MESSAGE));
        verifyNoInteractions(service);
    }
}
