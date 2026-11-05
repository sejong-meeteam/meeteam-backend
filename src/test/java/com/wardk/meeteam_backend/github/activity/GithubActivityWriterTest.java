package com.wardk.meeteam_backend.github.activity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.github.activity.*;
import com.wardk.meeteam_backend.domain.github.integration.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class GithubActivityWriterTest {
 @Test @SuppressWarnings("unchecked") void duplicateBackfillEnrichesStableIdWithoutDoubleCounting(){EntityManager em=mock(EntityManager.class);var links=mock(TypedQuery.class);var events=mock(TypedQuery.class);when(em.createQuery(contains("GithubProjectLink"),eq(GithubProjectLink.class))).thenReturn(links);when(links.setParameter(anyString(),any())).thenReturn(links);when(links.setLockMode(any())).thenReturn(links);when(links.getResultList()).thenReturn(List.of(new GithubProjectLink(1)));when(em.createQuery(contains("GithubActivityEvent"),eq(GithubActivityEvent.class))).thenReturn(events);when(events.setParameter(anyString(),any())).thenReturn(events);var existing=new GithubActivityEvent();when(events.getResultList()).thenReturn(List.of(existing));var writer=new GithubActivityWriter(em,mock(GithubWorkQueue.class));writer.event(3,"commit:abc","COMMIT",42L,null,"","",Instant.now());assertThat(existing.getGithubUserId()).isEqualTo(42);verify(em,never()).persist(any());}
 @Test @SuppressWarnings("unchecked") void webhookWithoutReviewDecisionPreservesApproved() throws Exception {EntityManager em=mock(EntityManager.class);var links=mock(TypedQuery.class);var prs=mock(TypedQuery.class);when(em.createQuery(contains("GithubProjectLink"),eq(GithubProjectLink.class))).thenReturn(links);when(links.setParameter(anyString(),any())).thenReturn(links);when(links.setLockMode(any())).thenReturn(links);when(links.getResultList()).thenReturn(List.of(new GithubProjectLink(1)));when(em.createQuery(contains("GithubPullRequest"),eq(GithubPullRequest.class))).thenReturn(prs);when(prs.setParameter(anyString(),any())).thenReturn(prs);var pr=new GithubPullRequest();pr.setReviewDecision("APPROVED");when(prs.getResultList()).thenReturn(List.of(pr));new GithubActivityWriter(em,mock(GithubWorkQueue.class)).pullRequest(3,new ObjectMapper().readTree("{\"number\":1,\"state\":\"open\",\"user\":{\"id\":42}}"),null);assertThat(pr.getReviewDecision()).isEqualTo("APPROVED");}
}
