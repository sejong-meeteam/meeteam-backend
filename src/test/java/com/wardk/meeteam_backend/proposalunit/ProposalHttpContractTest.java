package com.wardk.meeteam_backend.proposalunit;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.proposal.entity.ProposalStatus;
import com.wardk.meeteam_backend.domain.proposal.service.ProjectProposalService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.exception.handler.GlobalExceptionHandler;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.proposal.controller.ProposalController;
import com.wardk.meeteam_backend.web.proposal.dto.request.ProjectProposalRequest;
import com.wardk.meeteam_backend.web.proposal.dto.response.ProjectProposalResponse;
import com.wardk.meeteam_backend.web.proposal.dto.response.ProposableProjectResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: the JSON names, status codes and messages the frontend proposal dialog reads. */
@ExtendWith(MockitoExtension.class)
class ProposalHttpContractTest {

    private static final long ME = 7L;

    @Mock ProjectProposalService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        Member me = Member.createForTest("20260007@sju.ac.kr", "리더");
        ReflectionTestUtils.setField(me, "id", ME);
        var principal = new CustomSecurityUserDetails(me);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        mvc = MockMvcBuilders.standaloneSetup(new ProposalController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                // Spring Boot's ObjectMapper writes dates as ISO strings; standalone MockMvc does not by default.
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void proposeReturnsTheProposalWithOffsetTimestamp() throws Exception {
        when(service.propose(eq(101L), eq(ME), any())).thenReturn(new ProjectProposalResponse(
                1L, 101L, 203L, 1L, "기술 스택이 잘 맞아서 제안드려요.", ProposalStatus.PENDING,
                OffsetDateTime.parse("2026-11-19T13:20:00+09:00")));

        propose("{\"memberId\":203,\"jobPositionId\":1,\"message\":\"  기술 스택이 잘 맞아서 제안드려요.  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.proposalId").value(1))
                .andExpect(jsonPath("$.result.projectId").value(101))
                .andExpect(jsonPath("$.result.memberId").value(203))
                .andExpect(jsonPath("$.result.jobPositionId").value(1))
                .andExpect(jsonPath("$.result.message").value("기술 스택이 잘 맞아서 제안드려요."))
                .andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.createdAt").value("2026-11-19T13:20:00+09:00"));

        ArgumentCaptor<ProjectProposalRequest> sent = ArgumentCaptor.forClass(ProjectProposalRequest.class);
        verify(service).propose(eq(101L), eq(ME), sent.capture());
        assertThat(sent.getValue().message()).isEqualTo("기술 스택이 잘 맞아서 제안드려요.");
    }

    @Test
    void omittedMessageBecomesEmptyString() throws Exception {
        when(service.propose(anyLong(), anyLong(), any())).thenReturn(null);

        propose("{\"memberId\":203,\"jobPositionId\":1}").andExpect(status().isOk());

        ArgumentCaptor<ProjectProposalRequest> sent = ArgumentCaptor.forClass(ProjectProposalRequest.class);
        verify(service).propose(eq(101L), eq(ME), sent.capture());
        assertThat(sent.getValue().message()).isEmpty();
    }

    @Test
    void invalidRequestsAreProposal400WithTheSchemaWordingInFieldOrder() throws Exception {
        expectProposal400("{\"jobPositionId\":0,\"message\":\"" + "가".repeat(301) + "\"}", "제안할 회원을 확인해 주세요.");
        expectProposal400("{\"memberId\":-1,\"jobPositionId\":1}", "제안할 회원을 확인해 주세요.");
        expectProposal400("{\"memberId\":203,\"message\":\"" + "가".repeat(301) + "\"}", "제안할 포지션을 선택해 주세요.");
        expectProposal400("{\"memberId\":203,\"jobPositionId\":1,\"message\":\"" + "가".repeat(301) + "\"}",
                "메시지는 300자 이내로 작성해 주세요.");
        expectProposal400("{not json", "제안 내용을 확인해 주세요.");
        verifyNoInteractions(service);
    }

    @Test
    void messageLengthIsCheckedAfterTrimming() throws Exception {
        when(service.propose(anyLong(), anyLong(), any())).thenReturn(null);

        propose("{\"memberId\":203,\"jobPositionId\":1,\"message\":\"   " + "가".repeat(300) + "   \"}")
                .andExpect(status().isOk());
    }

    @Test
    void serviceRejectionsKeepStatusCodeAndUserFacingMessage() throws Exception {
        when(service.propose(anyLong(), anyLong(), any())).thenThrow(new CustomException(ErrorCode.PROPOSAL_DUPLICATE,
                "이미 '캠퍼스 메이커스' 프로젝트로 홍길동님에게 보낸 제안이 있어요."));
        propose("{\"memberId\":203,\"jobPositionId\":1,\"message\":\"\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("이미 '캠퍼스 메이커스' 프로젝트로 홍길동님에게 보낸 제안이 있어요."))
                .andExpect(jsonPath("$.result").value(nullValue()));

        reset(service);
        when(service.propose(anyLong(), anyLong(), any())).thenThrow(new CustomException(ErrorCode.PROPOSAL_RATE_LIMITED,
                "제안은 하루 20건까지 보낼 수 있어요. 내일 다시 시도해 주세요."));
        propose("{\"memberId\":203,\"jobPositionId\":1,\"message\":\"\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PROPOSAL_RATE_LIMITED"));

        reset(service);
        when(service.propose(anyLong(), anyLong(), any())).thenThrow(new CustomException(ErrorCode.PROPOSAL_DIFFERENT_UNIVERSITY));
        propose("{\"memberId\":203,\"jobPositionId\":1,\"message\":\"\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROPOSAL_DIFFERENT_UNIVERSITY"))
                .andExpect(jsonPath("$.message").value("같은 학교 회원에게만 제안할 수 있어요."));
    }

    @Test
    void proposableProjectsUseTheFrontendFieldNames() throws Exception {
        when(service.getProposableProjects(ME, 203L)).thenReturn(List.of(new ProposableProjectResponse(
                101L, "캠퍼스 메이커스",
                List.of(new ProposableProjectResponse.Position(1L, "프론트", "웹 프론트엔드")), false)));

        mvc.perform(get("/api/v1/members/203/proposable-projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result[0].projectId").value(101))
                .andExpect(jsonPath("$.result[0].projectName").value("캠퍼스 메이커스"))
                .andExpect(jsonPath("$.result[0].openPositions[0].jobPositionId").value(1))
                .andExpect(jsonPath("$.result[0].openPositions[0].jobFieldName").value("프론트"))
                .andExpect(jsonPath("$.result[0].openPositions[0].jobPositionName").value("웹 프론트엔드"))
                .andExpect(jsonPath("$.result[0].hasPendingProposal").value(false));
    }

    @Test
    void emptyProposableListIsAnEmptyArrayNotNull() throws Exception {
        when(service.getProposableProjects(ME, 203L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/members/203/proposable-projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").isArray())
                .andExpect(jsonPath("$.result").isEmpty());
    }

    private ResultActions propose(String body) throws Exception {
        return mvc.perform(post("/api/v1/projects/101/proposals").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void expectProposal400(String body, String message) throws Exception {
        propose(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROPOSAL400"))
                .andExpect(jsonPath("$.message").value(message));
    }
}
