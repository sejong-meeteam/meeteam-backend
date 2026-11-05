package com.wardk.meeteam_backend.github.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.github.integration.*;
import com.wardk.meeteam_backend.domain.github.activity.GithubActivityWriter;
import com.wardk.meeteam_backend.domain.github.collaboration.GithubCollaborationService;
import com.wardk.meeteam_backend.domain.github.webhook.GithubWebhookProcessor;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(excludeAutoConfiguration=org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
 properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes=GithubPrivacyVersionPersistenceTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class GithubPrivacyVersionPersistenceTest {
 @Configuration @EntityScan("com.wardk.meeteam_backend.domain") @Import(GithubWebhookProcessor.class)
 static class Config{
  @Bean EntityManager entityManager(EntityManagerFactory f){return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(f);}
  @Bean ObjectMapper mapper(){return new ObjectMapper();}
 }
 @Autowired EntityManager em;@Autowired PlatformTransactionManager manager;@Autowired GithubWebhookProcessor processor;@Autowired ObjectMapper mapper;
 @MockBean GithubActivityWriter activity;@MockBean GithubCollaborationService collaboration;
 <T>T tx(java.util.function.Supplier<T> action){return new TransactionTemplate(manager).execute(s->action.get());}
 @Test void noOpPrivatizationStillFencesOlderPublicSnapshot()throws Exception{
  long before=tx(()->{var l=new GithubProjectLink(3001);l.setInstallationId(2L);l.setInstallationActive(true);l.setGithubRepositoryId(3L);l.setRepositoryPrivate(true);em.persist(l);em.flush();return l.getRowVersion();});
  var payload=mapper.readTree("{\"event\":\"repository\",\"action\":\"privatized\",\"installation\":{\"id\":2},\"repository\":{\"id\":3,\"private\":true},\"received_at\":\"2026-11-05T00:00:00Z\"}");
  processor.apply(9001,payload,Map.of());
  tx(()->{var link=em.find(GithubProjectLink.class,3001L);assertThat(link.isRepositoryPrivate()).isTrue();assertThat(link.getRowVersion()).isGreaterThan(before);return null;});
 }
}
