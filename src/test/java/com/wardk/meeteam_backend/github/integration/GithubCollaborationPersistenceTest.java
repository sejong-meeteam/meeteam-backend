package com.wardk.meeteam_backend.github.integration;

import com.fasterxml.jackson.databind.*;
import com.wardk.meeteam_backend.domain.github.account.MemberGithubAccount;
import com.wardk.meeteam_backend.domain.github.collaboration.*;
import com.wardk.meeteam_backend.domain.github.integration.*;
import com.wardk.meeteam_backend.domain.github.repository.GithubAppClient;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.*;
import com.wardk.meeteam_backend.domain.projectmember.entity.*;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(excludeAutoConfiguration=org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
 properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes=GithubCollaborationPersistenceTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class GithubCollaborationPersistenceTest {
 @Configuration @EntityScan("com.wardk.meeteam_backend.domain")
 @Import({GithubCollaborationService.class,GithubCollaborationListener.class,GithubProjectAccess.class,GithubWorkQueue.class,
  GithubWorkWorker.class,GithubWorkTransactions.class,GithubInviteWorkHandler.class,GithubRevokeWorkHandler.class})
 static class Config{
  @Bean EntityManager entityManager(EntityManagerFactory f){return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(f);}
  @Bean ObjectMapper mapper(){return new ObjectMapper();}
 }
 @Autowired EntityManager em;@Autowired GithubCollaborationService service;@Autowired GithubWorkWorker worker;
 @Autowired PlatformTransactionManager manager;@Autowired ApplicationEventPublisher events;@Autowired ObjectMapper mapper;
 @MockBean GithubAppClient client;@MockBean NotificationSaveService notifications;@MockBean S3ImageUrlResolver images;
 long project,member,leader,githubId,leaderGithub;boolean fail;
 <T>T tx(java.util.function.Supplier<T> action){return new TransactionTemplate(manager).execute(s->action.get());}
 @BeforeEach void seed(){fail=false;tx(()->{
  em.createQuery("delete from GithubWork").executeUpdate();em.createQuery("delete from GithubCollaboratorGrant").executeUpdate();em.createQuery("delete from GithubCollaborator").executeUpdate();
  var l=Member.createForTest(UUID.randomUUID()+"@example.invalid","리더");var m=Member.createForTest(UUID.randomUUID()+"@example.invalid","팀원");em.persist(l);em.persist(m);em.flush();leader=l.getId();member=m.getId();githubId=member*100;leaderGithub=leader*100;
  var p=Project.builder().creator(l).name("연동 프로젝트").description("소개").projectCategory(ProjectCategory.CAPSTONE).platformCategory(PlatformCategory.WEB)
    .recruitmentStatus(Recruitment.RECRUITING).recruitmentDeadlineType(RecruitmentDeadlineType.END_DATE).build();em.persist(p);em.flush();project=p.getId();
  var pm=ProjectMember.createProjectMember(m,null,ProjectMemberRole.MEMBER);pm.assignProject(p);em.persist(pm);
  em.persist(new MemberGithubAccount(l,leaderGithub,"leader-dev","",LocalDateTime.now()));em.persist(new MemberGithubAccount(m,githubId,"member-dev","",LocalDateTime.now()));
  var link=new GithubProjectLink(project);link.setInstallationId(7L);link.setInstallationActive(true);link.setGithubRepositoryId(project*1000);
  link.setFullName("team/fixture"+project);link.setInstalledByMemberId(leader);link.setInstalledGithubUserId(leaderGithub);em.persist(link);return null;
 });
  when(client.request(anyLong(),anyString(),anyString(),any())).thenAnswer(call->{
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).as("GitHub calls must not hold a DB transaction").isFalse();
   String method=call.getArgument(1),path=call.getArgument(2);
   if(method.equals("GET")&&path.equals("/repositories/"+(project*1000)))return new GithubAppClient.Reply(200,mapper.createObjectNode().put("id",project*1000).put("full_name","team/fixture"+project));
   if(method.equals("GET")&&path.equals("/users/member-dev"))return new GithubAppClient.Reply(200,mapper.createObjectNode().put("id",githubId));
   if(method.equals("PUT")){if(fail)throw new IllegalStateException("private upstream details");return new GithubAppClient.Reply(201,mapper.createObjectNode().put("id",700));}
   if(method.equals("DELETE"))return new GithubAppClient.Reply(204,mapper.nullNode());
   throw new IllegalStateException("Unexpected metadata call");
  });
  when(client.paginate(anyLong(),anyString(),isNull())).thenAnswer(call->{
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
   return List.of(mapper.createObjectNode().put("id",700).set("invitee",mapper.createObjectNode().put("id",githubId)),
     mapper.createObjectNode().put("id",701).set("invitee",mapper.createObjectNode().put("id",99999999)));
  });
 }
 void joined(){tx(()->{events.publishEvent(new GithubDomainEvents.MemberJoined(project,member));return null;});}
 long jobs(){return tx(()->em.createQuery("select count(w) from GithubWork w",Long.class).getSingleResult());}
 void leave(boolean deleteAccount){tx(()->{
  em.createQuery("delete from ProjectMember pm where pm.project.id=:p and pm.member.id=:m").setParameter("p",project).setParameter("m",member).executeUpdate();
  if(deleteAccount)em.remove(em.find(MemberGithubAccount.class,member));
  events.publishEvent(new GithubDomainEvents.MemberRemoved(project,member,githubId,"member-dev"));return null;
 });}
 @Test void automaticInviteWaitsForCommitAndPersistsActualInvitation(){
  joined();verifyNoInteractions(client);assertThat(jobs()).isEqualTo(1);assertThat(worker.runOnce()).isTrue();
  var row=service.list(project,leader).stream().filter(c->c.memberId()==member).findFirst().orElseThrow();
  assertThat(row.accessStatus()).isEqualTo("INVITED");assertThat(row.invitedAt()).isNotNull();
  tx(()->{var grant=em.createQuery("select g from GithubCollaboratorGrant g where g.projectId=:p",GithubCollaboratorGrant.class).setParameter("p",project).getSingleResult();assertThat(grant.getInvitationId()).isEqualTo(700);return null;});
  assertThat(service.list(project,leader).stream().filter(c->c.memberId()==leader).findFirst().orElseThrow().accessStatus()).isEqualTo("ACTIVE");
 }
 @Test void accountDeletionStillAllowsNumericIdentityInvitationAndAccessRevocation(){
  joined();assertThat(worker.runOnce()).isTrue();clearInvocations(client);leave(true);assertThat(worker.runOnce()).isTrue();
  verify(client).request(7,"DELETE","/repos/team/fixture"+project+"/collaborators/member-dev",null);
  verify(client).request(7,"DELETE","/repos/team/fixture"+project+"/invitations/700",null);
  verify(client,never()).request(7,"DELETE","/repos/team/fixture"+project+"/invitations/701",null);
  assertThat(tx(()->em.createQuery("select g.status from GithubCollaboratorGrant g where g.projectId=:p",String.class).setParameter("p",project).getSingleResult())).isEqualTo("NOT_INVITED");
 }
 @Test void upstreamFailureCannotRollbackCommittedMembership(){
  fail=true;joined();assertThat(worker.runOnce()).isTrue();
  assertThat(tx(()->em.createQuery("select count(pm) from ProjectMember pm where pm.project.id=:p and pm.member.id=:m",Long.class).setParameter("p",project).setParameter("m",member).getSingleResult())).isEqualTo(1);
  assertThat(service.list(project,leader).stream().filter(c->c.memberId()==member).findFirst().orElseThrow().accessStatus()).isEqualTo("FAILED");
  assertThat(tx(()->em.createQuery("select w.failureReason from GithubWork w",String.class).getSingleResult())).doesNotContain("private");
 }
 @Test void revocationDoesNotDeleteIdentityNowOwnedByLeader(){
  joined();assertThat(worker.runOnce()).isTrue();leave(true);
  tx(()->{em.remove(em.find(MemberGithubAccount.class,leader));em.flush();em.persist(new MemberGithubAccount(em.find(Member.class,leader),githubId,"member-dev","",LocalDateTime.now()));return null;});
  clearInvocations(client);assertThat(worker.runOnce()).isTrue();verifyNoInteractions(client);
 }
 @Test void nonLeaderCannotReadOrMutateGithubPermissions(){
  assertThatThrownBy(()->service.list(project,member)).isInstanceOf(com.wardk.meeteam_backend.global.exception.CustomException.class);
  assertThatThrownBy(()->service.invite(project,member,leader)).isInstanceOf(com.wardk.meeteam_backend.global.exception.CustomException.class);verifyNoInteractions(client);
 }
 @Test void linkedAccountIsRequiredForManualInviteWithExactConflictCode(){
  tx(()->{em.remove(em.find(MemberGithubAccount.class,member));return null;});
  assertThatThrownBy(()->service.invite(project,leader,member)).isInstanceOfSatisfying(com.wardk.meeteam_backend.global.exception.CustomException.class,
    error->assertThat(error.getErrorCode()).isEqualTo(com.wardk.meeteam_backend.global.response.ErrorCode.GITHUB_ACCOUNT_NOT_LINKED));verifyNoInteractions(client);
 }
 @Test void disablingAutomaticInvitesCancelsWaitingGrantWithoutFakeStatus(){
  joined();tx(()->{em.find(GithubProjectLink.class,project).setAutoInviteOnApproval(false);return null;});
  assertThat(worker.runOnce()).isTrue();verifyNoInteractions(client);
  assertThat(service.list(project,leader).stream().filter(c->c.memberId()==member).findFirst().orElseThrow().accessStatus()).isEqualTo("NOT_INVITED");
 }
 @Test void reconnectDoesNotReinviteAlreadyRecordedIdentity(){
  joined();assertThat(worker.runOnce()).isTrue();clearInvocations(client);
  tx(()->{events.publishEvent(new GithubDomainEvents.AccountLinked(member));return null;});
  assertThat(worker.runOnce()).isFalse();verifyNoInteractions(client);
 }
 @Test void rollbackDoesNotEnqueueAutomaticInvite(){
  assertThatThrownBy(()->tx(()->{events.publishEvent(new GithubDomainEvents.MemberJoined(project,member));throw new IllegalStateException();})).isInstanceOf(IllegalStateException.class);
  assertThat(jobs()).isZero();verifyNoInteractions(client);
 }
}
