package com.wardk.meeteam_backend.github.activity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.github.activity.*;
import com.wardk.meeteam_backend.domain.github.integration.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import com.wardk.meeteam_backend.global.exception.CustomException;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class GithubActivityQueryTest {
 private final EntityManager em=mock(EntityManager.class);private final GithubProjectAccess access=mock(GithubProjectAccess.class);private final Clock clock=Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"),ZoneOffset.UTC);
 private GithubActivityQuery query(){return new GithubActivityQuery(em,access,new ObjectMapper(),clock);}
 private GithubProjectLink linked(){var link=new GithubProjectLink(1);link.setInstallationActive(true);link.setInstallationId(2L);link.setGithubRepositoryId(3L);link.setRepositoryPrivate(true);link.setFullName("secret/repository");link.setHtmlUrl("https://github.com/secret/repository");link.setDescription("confidential description");when(em.find(GithubProjectLink.class,1L)).thenReturn(link);return link;}
 @Test @SuppressWarnings("unchecked") void anonymousPrivateActivityRedactsIdentityButPreservesAggregates(){linked();var snapshot=new GithubRepositorySnapshot(3);snapshot.setSyncedAt(clock.instant());snapshot.setOpenIssueCount(4);snapshot.setContributors("[{\"id\":987,\"login\":\"secret-person\",\"contributions\":10}]");when(em.find(GithubRepositorySnapshot.class,3L)).thenReturn(snapshot);TypedQuery<Instant> times=mock(TypedQuery.class);when(em.createQuery(contains("e.occurredAt"),eq(Instant.class))).thenReturn(times);when(times.setParameter(anyString(),any())).thenReturn(times);when(times.getResultList()).thenReturn(List.of(clock.instant()));var result=query().activity(1,null);assertThat(result.contributors()).isEmpty();assertThat(result.repository().fullName()).isEmpty();assertThat(result.repository().htmlUrl()).isEmpty();assertThat(result.repository().description()).isEmpty();assertThat(result.repository().id()).isZero();assertThat(result.openIssueCount()).isEqualTo(4);assertThat(result.weeklyCommits()).containsExactly(0,0,0,0,0,0,0,1);verify(access).requirePublic(1);}
 @Test void rejectsNonMondayAndFutureWeeks(){linked();assertThatThrownBy(()->query().weekly(1,5L,LocalDate.parse("2026-11-03"))).isInstanceOf(CustomException.class);assertThatThrownBy(()->query().weekly(1,5L,LocalDate.parse("2026-11-09"))).isInstanceOf(CustomException.class).hasMessage("아직 시작하지 않은 주입니다.");verify(access,times(2)).requireTeam(1,5L);}
 @Test void unlinkedPublicActivityIsNullWithoutNetworkOrSnapshot(){assertThat(query().activity(1,null)).isNull();verify(access).requirePublic(1);verify(em,never()).find(eq(GithubRepositorySnapshot.class),any());}

 @Test void linkedButNeverSyncedIsPendingNotUnlinkedOrFabricatedZero(){linked();String message="GitHub 개발 현황을 동기화하고 있습니다. 잠시 후 다시 시도해 주세요.";assertThatThrownBy(()->query().activity(1,null)).isInstanceOf(CustomException.class).hasMessage(message);assertThatThrownBy(()->query().progress(1,5L)).isInstanceOf(CustomException.class).hasMessage(message);assertThatThrownBy(()->query().weekly(1,5L,LocalDate.parse("2026-11-02"))).isInstanceOf(CustomException.class).hasMessage(message);var unsynced=new GithubRepositorySnapshot(3);when(em.find(GithubRepositorySnapshot.class,3L)).thenReturn(unsynced);assertThatThrownBy(()->query().activity(1,null)).isInstanceOf(CustomException.class).hasMessage(message);}
}
