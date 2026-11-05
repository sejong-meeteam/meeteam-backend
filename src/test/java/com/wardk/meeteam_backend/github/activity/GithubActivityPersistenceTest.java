package com.wardk.meeteam_backend.github.activity;
import com.wardk.meeteam_backend.domain.github.activity.*;
import com.wardk.meeteam_backend.domain.github.integration.*;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class GithubActivityPersistenceTest {
 @Test void lockedWriterPersistsOnceAndEnrichesUnknownAuthorOnBackfill(){
  var config=new Configuration().addAnnotatedClass(GithubProjectLink.class).addAnnotatedClass(GithubRepositorySnapshot.class).addAnnotatedClass(GithubActivityEvent.class).addAnnotatedClass(GithubPullRequest.class)
   .setProperty("hibernate.connection.driver_class","org.h2.Driver").setProperty("hibernate.connection.url","jdbc:h2:mem:githubactivity;MODE=MySQL;DB_CLOSE_DELAY=-1")
   .setProperty("hibernate.hbm2ddl.auto","create-drop").setProperty("hibernate.show_sql","false");
  try(var factory=config.buildSessionFactory();var em=factory.createEntityManager()){
   em.getTransaction().begin();var link=new GithubProjectLink(1);link.setInstallationId(2L);link.setInstallationActive(true);link.setGithubRepositoryId(3L);em.persist(link);em.getTransaction().commit();
   var writer=new GithubActivityWriter(em,mock(GithubWorkQueue.class));Instant at=Instant.parse("2026-11-05T00:00:00Z");
   em.getTransaction().begin();writer.event(3,"commit:abc","COMMIT",null,null,"","",at);em.getTransaction().commit();em.clear();
   em.getTransaction().begin();writer.event(3,"commit:abc","COMMIT",42L,null,"","",at);em.getTransaction().commit();em.clear();
   var events=em.createQuery("select e from GithubActivityEvent e",GithubActivityEvent.class).getResultList();assertThat(events).hasSize(1);assertThat(events.get(0).getGithubUserId()).isEqualTo(42L);
   em.getTransaction().begin();var saved=em.find(GithubProjectLink.class,1L);saved.setRepositoryPrivate(true);saved.setFullName("private/hidden");var snapshot=new GithubRepositorySnapshot(3);snapshot.setSyncedAt(at);snapshot.setLastPushedAt(at);snapshot.setContributors("[{\"id\":42,\"login\":\"hidden-person\"}]");em.persist(snapshot);var pendingLink=new GithubProjectLink(2);pendingLink.setInstallationActive(true);pendingLink.setInstallationId(2L);pendingLink.setGithubRepositoryId(4L);em.persist(pendingLink);em.persist(new GithubRepositorySnapshot(4));em.getTransaction().commit();em.clear();
   var query=new GithubActivityQuery(em,mock(GithubProjectAccess.class),new ObjectMapper(),Clock.fixed(at,ZoneOffset.UTC));var activity=query.activity(1,null);assertThat(activity.contributors()).isEmpty();assertThat(activity.repository().fullName()).isEmpty();assertThat(activity.weeklyCommits()).containsExactly(0,0,0,0,0,0,0,1);assertThat(query.summaries(List.of(1L,2L))).containsOnlyKeys(1L);assertThat(query.summaries(List.of(1L)).get(1L).activityLevel()).isEqualTo("ACTIVE");
  }
 }
}
