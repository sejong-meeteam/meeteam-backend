package com.wardk.meeteam_backend.schoolregistrationunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.global.auth.client.SejongPortalProfile;
import com.wardk.meeteam_backend.global.auth.cookie.*;
import com.wardk.meeteam_backend.global.auth.service.AuthService;
import com.wardk.meeteam_backend.global.auth.service.dto.*;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.exception.handler.GlobalExceptionHandler;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.auth.controller.AuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone MockMvc: exercises request validation, mapping and server-owned response fields. */
@ExtendWith(MockitoExtension.class)
class SchoolRegistrationHttpContractTest {
    @Mock AuthService auth;
    @Mock RefreshTokenCookieProvider refreshCookies;
    @Mock AccessTokenCookieProvider accessCookies;
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, refreshCookies, accessCookies))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void newLoginIncludesOfficialProfileAndCodeWithoutTokenCookies() throws Exception {
        var profile = new SejongPortalProfile("20261234", "School Name", "Computer Science");
        when(auth.sejongLogin(any())).thenReturn(SejongLoginResult.newMember("synthetic-code", profile));
        var response = mvc.perform(post("/api/v1/auth/login/sejong").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"20261234\",\"password\":\"synthetic-input\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.code").value("synthetic-code"))
                .andExpect(jsonPath("$.result.verifiedProfile.studentId").value("20261234"))
                .andExpect(jsonPath("$.result.verifiedProfile.name").value("School Name"))
                .andExpect(jsonPath("$.result.verifiedProfile.department").value("Computer Science"))
                .andExpect(jsonPath("$.result.accessToken").doesNotExist())
                .andExpect(jsonPath("$.result.refreshToken").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn();
        var tree = new ObjectMapper().readTree(response.getResponse().getContentAsString()).path("result");
        assertThat(tree.has("newMember") ? tree.path("newMember").asBoolean() : tree.path("isNewMember").asBoolean()).isTrue();
        verifyNoInteractions(accessCookies, refreshCookies);
    }

    @Test void existingLoginOmitsVerifiedProfileAndStillIssuesBothCookies() throws Exception {
        when(auth.sejongLogin(any())).thenReturn(SejongLoginResult.existingMember("synthetic-access", "synthetic-refresh"));
        mvc.perform(post("/api/v1/auth/login/sejong").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"20261234\",\"password\":\"synthetic-input\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.verifiedProfile").doesNotExist())
                .andExpect(jsonPath("$.result.accessToken").doesNotExist())
                .andExpect(jsonPath("$.result.refreshToken").doesNotExist());
        verify(accessCookies).addCookie(any(), eq("synthetic-access"));
        verify(refreshCookies).addCookie(any(), eq("synthetic-refresh"));
    }

    @Test void failedLoginDoesNotReturnCodeProfileOrSetCookies() throws Exception {
        when(auth.sejongLogin(any())).thenThrow(new CustomException(ErrorCode.BAD_CREDENTIALS));
        mvc.perform(post("/api/v1/auth/login/sejong").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"20261234\",\"password\":\"synthetic-input\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.result.code").doesNotExist())
                .andExpect(jsonPath("$.result.verifiedProfile").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        verifyNoInteractions(accessCookies, refreshCookies);
    }

    @Test void multipartRegistrationNeedsNoManuallyEnteredName() throws Exception {
        when(auth.sejongRegister(any(), isNull())).thenReturn(new TokenExchangeResult("synthetic-access", "synthetic-refresh"));
        mvc.perform(multipart("/api/v1/auth/register/sejong").file(registrationPart(null)))
                .andExpect(status().isOk());
        var command = ArgumentCaptor.forClass(SejongRegisterCommand.class);
        verify(auth).sejongRegister(command.capture(), isNull());
        assertThat(command.getValue().name()).isNull();
        assertThat(command.getValue().code()).isEqualTo("synthetic-code");
        assertThat(command.getValue().birthDate()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(command.getValue().jobPositions()).hasSize(1);
        verify(accessCookies).addCookie(any(), eq("synthetic-access"));
        verify(refreshCookies).addCookie(any(), eq("synthetic-refresh"));
    }

    @Test void legacyNameFieldRemainsAcceptedForBackwardCompatibleClients() throws Exception {
        when(auth.sejongRegister(any(), isNull())).thenReturn(new TokenExchangeResult("synthetic-access", "synthetic-refresh"));
        mvc.perform(multipart("/api/v1/auth/register/sejong").file(registrationPart("Legacy Client")))
                .andExpect(status().isOk());
        // The service tests separately prove this compatibility input never becomes authoritative.
        verify(auth).sejongRegister(argThat(c -> "Legacy Client".equals(c.name())), isNull());
    }

    @Test void invalidRegistrationCodeCannotSetCookies() throws Exception {
        when(auth.sejongRegister(any(), isNull())).thenThrow(new CustomException(ErrorCode.INVALID_SEJONG_CODE));
        mvc.perform(multipart("/api/v1/auth/register/sejong").file(registrationPart(null)))
                .andExpect(status().is4xxClientError()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        verifyNoInteractions(accessCookies, refreshCookies);
    }

    private MockMultipartFile registrationPart(String legacyName) {
        String json = "{\"code\":\"synthetic-code\",\"birthDate\":\"2000-01-02\",\"gender\":\"MALE\","
                + "\"jobPositions\":[{\"jobFieldCode\":\"BACKEND\",\"jobPositionCode\":\"JAVA_SPRING\",\"techStacks\":[]}]"
                + (legacyName == null ? "" : ",\"name\":\"" + legacyName + "\"") + "}";
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }
}
