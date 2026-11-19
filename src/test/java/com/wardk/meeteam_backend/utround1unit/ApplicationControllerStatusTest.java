package com.wardk.meeteam_backend.utround1unit;

import com.wardk.meeteam_backend.domain.application.entity.ApplicationStatus;
import com.wardk.meeteam_backend.domain.application.service.ProjectApplicationService;
import com.wardk.meeteam_backend.global.exception.handler.GlobalExceptionHandler;
import com.wardk.meeteam_backend.web.application.controller.ApplicationController;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Synthetic HTTP requests only. The service is mocked; no DB. */
class ApplicationControllerStatusTest {
    private static final String LEADER = "leader@example.invalid";
    private ProjectApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProjectApplicationService.class);
        when(service.getApplicationList(anyLong(), any(), anyString())).thenReturn(List.of());
        var principal = mock(CustomSecurityUserDetails.class);
        when(principal.getUsername()).thenReturn(LEADER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        mvc = standaloneSetup(new ApplicationController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void omittedStatusKeepsTheCurrentPendingList() throws Exception {
        mvc.perform(get("/api/v1/projects/1/applications")).andExpect(status().isOk());
        verify(service).getApplicationList(1L, ApplicationStatus.PENDING, LEADER);
    }

    @Test
    void decidedStatusesArePassedThrough() throws Exception {
        mvc.perform(get("/api/v1/projects/1/applications").param("status", "ACCEPTED")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects/1/applications").param("status", "REJECTED")).andExpect(status().isOk());
        verify(service).getApplicationList(1L, ApplicationStatus.ACCEPTED, LEADER);
        verify(service).getApplicationList(1L, ApplicationStatus.REJECTED, LEADER);
    }

    @Test
    void cancelledAndUnknownStatusesAreRejectedWithoutQuerying() throws Exception {
        mvc.perform(get("/api/v1/projects/1/applications").param("status", "CANCELLED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
        mvc.perform(get("/api/v1/projects/1/applications").param("status", "DONE"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void decisionOtherThanAcceptOrRejectIsRejectedBeforeAnyLock() throws Exception {
        for (String decision : List.of("PENDING", "CANCELLED")) {
            mvc.perform(post("/api/v1/projects/1/applications/9/decision")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"" + decision + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON400"));
        }
        verifyNoInteractions(service);
    }
}
