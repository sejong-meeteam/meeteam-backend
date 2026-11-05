package com.wardk.meeteam_backend.github.integration;
import com.wardk.meeteam_backend.domain.github.activity.*;
import com.wardk.meeteam_backend.web.github.integration.GithubProjectCardAdvice;
import com.wardk.meeteam_backend.web.mainpage.dto.response.ProjectCardResponse;
import com.wardk.meeteam_backend.web.member.dto.response.MemberProfileResponse;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class GithubCardEnrichmentTest {
 @Test void pageIsEnrichedInOneBatchAndOldCacheValueIsOverwritten(){
  var query=mock(GithubActivityQuery.class);var advice=new GithubProjectCardAdvice(query);
  var a=ProjectCardResponse.builder().projectId(1L).githubActivity(new ProjectCardResponse.GithubActivity("ACTIVE",Instant.EPOCH)).build();
  var b=ProjectCardResponse.builder().projectId(2L).build();
  when(query.summaries(List.of(1L,2L))).thenReturn(Map.of(2L,new GithubActivitySummary("MODERATE",Instant.EPOCH)));
  var body=SuccessResponse.onSuccess(new PageImpl<>(List.of(a,b)));
  assertThat(advice.beforeBodyWrite(body,null,null,null,null,null)).isSameAs(body);
  assertThat(a.getGithubActivity()).isNull();assertThat(b.getGithubActivity().activityLevel()).isEqualTo("MODERATE");verify(query,times(1)).summaries(List.of(1L,2L));
 }
 @Test void profileCardsAreAlsoBatchedAndEmptyResponsesDoNotQuery(){
  var query=mock(GithubActivityQuery.class);var advice=new GithubProjectCardAdvice(query);var profile=new MemberProfileResponse();
  profile.setProjectCards(List.of(ProjectCardResponse.builder().projectId(1L).build()));when(query.summaries(List.of(1L))).thenReturn(Map.of());
  advice.beforeBodyWrite(SuccessResponse.onSuccess(profile),null,null,null,null,null);
  advice.beforeBodyWrite(SuccessResponse.onSuccess(List.of()),null,null,null,null,null);verify(query,times(1)).summaries(any());
 }
 @Test void legacyListResponsesAlsoGetOneBatchedLookup(){
  var query=mock(GithubActivityQuery.class);var advice=new GithubProjectCardAdvice(query);
  var legacy=com.wardk.meeteam_backend.web.project.dto.response.ProjectListResponse.builder().projectId(3L).build();
  when(query.summaries(List.of(3L))).thenReturn(Map.of(3L,new GithubActivitySummary("ACTIVE",Instant.EPOCH)));
  advice.beforeBodyWrite(SuccessResponse.onSuccess(List.of(legacy)),null,null,null,null,null);
  assertThat(legacy.getGithubActivity().activityLevel()).isEqualTo("ACTIVE");verify(query).summaries(List.of(3L));
 }
 @Test void unlinkedBadgeIsExplicitNullAndCachePayloadRemainsSerializable()throws Exception{
  var card=ProjectCardResponse.builder().projectId(1L).build();
  assertThat(new ObjectMapper().writeValueAsString(card)).contains("\"githubActivity\":null");
  card.setGithubActivity(new ProjectCardResponse.GithubActivity("ACTIVE",Instant.EPOCH));
  var bytes=new java.io.ByteArrayOutputStream();try(var stream=new java.io.ObjectOutputStream(bytes)){stream.writeObject(card);}
  assertThat(bytes.size()).isPositive();
 }
}
