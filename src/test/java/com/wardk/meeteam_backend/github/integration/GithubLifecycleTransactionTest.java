package com.wardk.meeteam_backend.github.integration;

import com.wardk.meeteam_backend.domain.github.account.*;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthClient.Identity;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubOAuthStateService.Validated;
import com.wardk.meeteam_backend.domain.github.integration.*;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(excludeAutoConfiguration=org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
 properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.sql.init.mode=never","integration.github.app.enabled=true"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes=GithubLifecycleTransactionTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class GithubLifecycleTransactionTest {
 @Configuration @EntityScan("com.wardk.meeteam_backend.domain")
 @EnableJpaRepositories(basePackageClasses=MemberGithubAccountRepository.class)
 @Import({GithubIntegrationConfiguration.class,GithubAccountService.class,GithubLifecycleAspect.class,GithubProjectCleanup.class,GithubWorkQueue.class})
 static class Config{
  @Bean EntityManager entityManager(EntityManagerFactory f){return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(f);}
  @Bean Recorder recorder(GithubWorkQueue queue){return new Recorder(queue);}
 }
 static class Recorder{
  final GithubWorkQueue queue;int count;boolean fail;
  Recorder(GithubWorkQueue queue){this.queue=queue;}
  @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
  public void account(GithubDomainEvents.AccountLinked event){
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();count++;
   queue.enqueue("PROBE",null,event.memberId(),0,"{}",null);
   if(fail)throw new IllegalStateException("simulate original commit rollback");
  }
 }
 @Autowired EntityManager em;@Autowired GithubAccountService service;@Autowired PlatformTransactionManager manager;
 @Autowired Recorder recorder;@Autowired ApplicationEventPublisher events;
 long id;
 <T>T tx(java.util.function.Supplier<T> action){return new TransactionTemplate(manager).execute(s->action.get());}
 @BeforeEach void seed(){recorder.count=0;recorder.fail=false;id=tx(()->{var m=Member.createForTest(UUID.randomUUID()+"@example.invalid","테스트");em.persist(m);em.flush();return m.getId();});}
 Validated state(){return new Validated(id,0,0,"/settings","v".repeat(43));}
 long jobs(){return tx(()->em.createQuery("select count(w) from GithubWork w where w.memberId=:id",Long.class).setParameter("id",id).getSingleResult());}
 @Test void actualAccountServiceEmitsDurableJobBeforeItsTransactionCommits(){
  service.connect(state(),new Identity(id*100,"unit-dev",""));
  assertThat(recorder.count).isEqualTo(1);assertThat(jobs()).isEqualTo(1);
  assertThat(tx(()->em.find(MemberGithubAccount.class,id))).isNotNull();
 }
 @Test void originCommitFailureRollsBackAccountAndQueuedJobTogether(){
  recorder.fail=true;assertThatThrownBy(()->service.connect(state(),new Identity(id*100,"unit-dev",""))).isInstanceOf(RuntimeException.class);
  assertThat(jobs()).isZero();assertThat(tx(()->em.find(MemberGithubAccount.class,id))).isNull();
  assertThat(tx(()->em.find(Member.class,id).getGithubLinkVersion())).isZero();
 }
 @Test void failedOriginalMutationNeverEmitsAccountLinked(){
  var stale=new Validated(id,99,0,"/settings","v".repeat(43));
  assertThatThrownBy(()->service.connect(stale,new Identity(id*100,"unit-dev",""))).isInstanceOf(RuntimeException.class);
  assertThat(recorder.count).isZero();assertThat(jobs()).isZero();
 }
 @Test void projectDeletionRemovesDelegationInSameCommit(){
  long project=tx(()->{var p=Project.builder().creator(em.find(Member.class,id)).name("프로젝트").description("소개")
    .projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB).recruitmentStatus(Recruitment.RECRUITING)
    .recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE).build();em.persist(p);em.flush();var link=new GithubProjectLink(p.getId());
    link.setInstallationId(123L);link.setInstallationActive(true);link.setGithubRepositoryId(456L);em.persist(link);return p.getId();});
  tx(()->{events.publishEvent(new GithubDomainEvents.ProjectDeleted(project));return null;});
  assertThat(tx(()->em.find(GithubProjectLink.class,project))).isNull();
 }
}
