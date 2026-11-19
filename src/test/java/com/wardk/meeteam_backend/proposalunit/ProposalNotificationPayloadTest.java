package com.wardk.meeteam_backend.proposalunit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.NotificationEvent;
import com.wardk.meeteam_backend.domain.notification.entity.Notification;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.domain.notification.repository.EmitterRepository;
import com.wardk.meeteam_backend.domain.notification.service.SSENotificationService;
import com.wardk.meeteam_backend.domain.notification.service.SseEventPublisher;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.proposal.entity.ProjectProposal;
import com.wardk.meeteam_backend.domain.proposal.repository.ProjectProposalRepository;
import com.wardk.meeteam_backend.fixture.ProjectFixture;
import com.wardk.meeteam_backend.global.config.RedisConfig;
import com.wardk.meeteam_backend.web.notification.SsePublishMessage;
import com.wardk.meeteam_backend.web.notification.dto.response.NotificationResponse;
import com.wardk.meeteam_backend.web.notification.factory.NotificationPayloadFactory;
import com.wardk.meeteam_backend.web.notification.strategy.ProjectApplyPayloadStrategy;
import com.wardk.meeteam_backend.web.notification.strategy.ProjectProposalReceivedPayloadStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** The receiver's real-time event and notification list both carry the payload the frontend renders. */
class ProposalNotificationPayloadTest {

    private final ObjectMapper bootMapper = Jackson2ObjectMapperBuilder.json().build();

    private final MemberRepository members = mock(MemberRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectProposalRepository proposals = mock(ProjectProposalRepository.class);
    private final SseEventPublisher publisher = mock(SseEventPublisher.class);
    private final SSENotificationService sse = new SSENotificationService(mock(EmitterRepository.class), members, projects,
            proposals, new NotificationPayloadFactory(List.of(new ProjectProposalReceivedPayloadStrategy(),
                    new ProjectApplyPayloadStrategy())), publisher);

    private Member leader, receiver;
    private Project project;

    @BeforeEach
    void seed() {
        leader = member(1L, "김리더");
        receiver = member(2L, "홍길동");
        project = ProjectFixture.withName("캠퍼스 메이커스", leader);
        ReflectionTestUtils.setField(project, "id", 101L);
        when(members.findById(1L)).thenReturn(Optional.of(leader));
        when(members.findById(2L)).thenReturn(Optional.of(receiver));
        when(projects.findById(101L)).thenReturn(Optional.of(project));
    }

    @Test
    void realtimeEventIsNamedByTypeAndSurvivesTheRedisRoundTrip() throws Exception {
        stubProposal("기술 스택이 잘 맞아서 제안드려요.");

        JsonNode envelope = publishedEnvelope();

        assertThat(envelope.path("type").asText()).isEqualTo("PROJECT_PROPOSAL_RECEIVED");
        JsonNode data = envelope.path("data");
        assertThat(data.path("projectId").asLong()).isEqualTo(101L);
        assertThat(data.path("projectName").asText()).isEqualTo("캠퍼스 메이커스");
        assertThat(data.path("proposerId").asLong()).isEqualTo(1L);
        assertThat(data.path("proposerName").asText()).isEqualTo("김리더");
        assertThat(data.path("jobFieldName").asText()).isEqualTo("프론트");
        assertThat(data.path("jobPositionName").asText()).isEqualTo("웹 프론트엔드");
        assertThat(data.path("proposalMessage").asText()).isEqualTo("기술 스택이 잘 맞아서 제안드려요.");
    }

    @Test
    void realtimeEventOmitsEmptyMessageAndMasksPrivateProposerName() throws Exception {
        leader.updateNamePrivacy(true);
        stubProposal("");

        JsonNode data = publishedEnvelope().path("data");

        assertThat(data.has("proposalMessage")).isFalse();
        assertThat(data.path("proposerName").asText()).isEqualTo("김**");
    }

    @Test
    void otherNotificationTypesKeepTheirListPayloadShape() throws Exception {
        Notification apply = Notification.builder().receiver(receiver).project(project).actorId(1L)
                .type(NotificationType.PROJECT_APPLY).isRead(false).build();
        ReflectionTestUtils.setField(apply, "createdAt", LocalDateTime.now());

        JsonNode payload = bootMapper.valueToTree(new NotificationResponse(apply)).path("payload");

        Set<String> keys = new java.util.HashSet<>();
        payload.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("projectId", "projectName", "receiverId", "receiverName");
    }

    private void stubProposal(String message) {
        JobField field = JobField.of(JobFieldCode.FRONTEND);
        JobPosition position = JobPosition.of(field, JobPositionCode.WEB_FRONTEND);
        ProjectProposal proposal = ProjectProposal.pending(project, leader, receiver, position, message);
        ReflectionTestUtils.setField(proposal, "id", 55L);
        when(proposals.findById(55L)).thenReturn(Optional.of(proposal));
    }

    private JsonNode publishedEnvelope() throws Exception {
        sse.notify(NotificationEvent.proposalReceived(2L, 101L, 1L, 55L));
        ArgumentCaptor<SsePublishMessage> published = ArgumentCaptor.forClass(SsePublishMessage.class);
        verify(publisher).publish(published.capture());
        assertThat(published.getValue().getReceiverId()).isEqualTo(2L);

        var redis = new RedisConfig().redisValueSerializer(bootMapper);
        SsePublishMessage restored = (SsePublishMessage) redis.deserialize(redis.serialize(published.getValue()));
        return bootMapper.valueToTree(restored.getEnvelope());
    }

    private static Member member(long id, String name) {
        Member member = Member.createForTest(id + "@sju.ac.kr", name);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
