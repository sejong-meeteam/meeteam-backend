package com.wardk.meeteam_backend.github.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.github.collaboration.*;
import com.wardk.meeteam_backend.domain.github.integration.*;
import com.wardk.meeteam_backend.domain.github.account.MemberGithubAccount;
import com.wardk.meeteam_backend.domain.github.repository.GithubAppClient;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.web.notification.strategy.*;
import com.wardk.meeteam_backend.web.notification.context.NotificationContext;
import com.wardk.meeteam_backend.web.notification.payload.ProjectEndedPayload;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class GithubCollaborationSafetyTest {
 EntityManager em; GithubProjectAccess access; GithubAppClient client; GithubWorkQueue queue; ObjectMapper mapper;
 List<GithubCollaboratorGrant> history; GithubCollaborationService service; GithubCollaborator c; GithubProjectLink link; Project project; MemberGithubAccount account;
 @BeforeEach @SuppressWarnings("unchecked") void setup(){
  em=mock(EntityManager.class);access=mock(GithubProjectAccess.class);client=mock(GithubAppClient.class);queue=mock(GithubWorkQueue.class);mapper=new ObjectMapper();
  PlatformTransactionManager manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
  service=new GithubCollaborationService(em,access,client,queue,mapper,mock(NotificationSaveService.class),mock(ApplicationEventPublisher.class),mock(S3ImageUrlResolver.class),manager);
  link=new GithubProjectLink(1);link.setInstallationActive(true);link.setInstallationId(5L);link.setGithubRepositoryId(10L);link.setFullName("org/repo");link.setBindingVersion(2);
  when(em.find(eq(GithubProjectLink.class),eq(1L))).thenReturn(link);when(em.find(eq(GithubProjectLink.class),eq(1L),eq(LockModeType.PESSIMISTIC_WRITE))).thenReturn(link);
  project=mock(Project.class);when(em.find(Project.class,1L)).thenReturn(project);
  account=mock(MemberGithubAccount.class);when(account.getGithubUserId()).thenReturn(9L);when(em.find(MemberGithubAccount.class,3L)).thenReturn(account);
  c=new GithubCollaborator(1,3);c.setGithubUserId(9);c.setBindingVersion(2);c.setLogin("alice");
  TypedQuery<GithubCollaborator> query=mock(TypedQuery.class);when(em.createQuery(anyString(),eq(GithubCollaborator.class))).thenReturn(query);when(query.setParameter(anyString(),any())).thenReturn(query);when(query.setLockMode(any())).thenReturn(query);when(query.getResultList()).thenReturn(List.of(c));
  history=new ArrayList<>();TypedQuery<GithubCollaboratorGrant> gq=mock(TypedQuery.class);when(em.createQuery(anyString(),eq(GithubCollaboratorGrant.class))).thenReturn(gq);when(gq.setParameter(anyString(),any())).thenReturn(gq);when(gq.getResultList()).thenAnswer(i->new ArrayList<>(history));doAnswer(i->{if(i.getArgument(0) instanceof GithubCollaboratorGrant g)history.add(g);return null;}).when(em).persist(any());
  TypedQuery<MemberGithubAccount> aq=mock(TypedQuery.class);when(em.createQuery(anyString(),eq(MemberGithubAccount.class))).thenReturn(aq);when(aq.setParameter(anyString(),any())).thenReturn(aq);when(aq.getResultList()).thenReturn(List.of());
  TypedQuery<GithubProjectLink> lq=mock(TypedQuery.class);when(em.createQuery(anyString(),eq(GithubProjectLink.class))).thenReturn(lq);when(lq.setParameter(anyString(),any())).thenReturn(lq);when(lq.getResultList()).thenReturn(List.of(link));
  when(client.paginate(anyLong(),anyString(),isNull())).thenReturn(List.of());
  when(access.isTeam(1,3L)).thenReturn(true);
 }
 GithubWorkHandler.Job job(String kind,long version,Long invitation)throws Exception{return new GithubWorkHandler.Job(1,kind,1L,3L,version,mapper.writeValueAsString(new GithubCollaborationService.Snapshot(1,3,version,5,"org/repo",9,"alice",invitation)));}
 @Test void staleBindingNeverCallsGithub()throws Exception{service.handleInvite(job("COLLAB_INVITE",1,null));verifyNoInteractions(client);}
 @Test void removedMemberCannotBeInvited()throws Exception{when(access.isTeam(1,3L)).thenReturn(false);service.handleInvite(job("COLLAB_INVITE",2,null));verifyNoInteractions(client);}
 @Test void rejoinedMemberCannotBeRevoked()throws Exception{service.handleRevoke(job("COLLAB_REVOKE",2,88L));verifyNoInteractions(client);}
 @Test void leaderNeverRevoked()throws Exception{when(project.isLeader(3L)).thenReturn(true);service.handleRevoke(job("COLLAB_REVOKE",2,null));verifyNoInteractions(client);}
 @Test void reusedLoginFailsBeforePutAndPersistsSanitizedFailure()throws Exception{
  when(client.request(5,"GET","/users/alice",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":999}")));
  assertThrows(IllegalStateException.class,()->service.handleInvite(job("COLLAB_INVITE",2,null)));
  verify(client,times(1)).request(anyLong(),anyString(),anyString(),any());assertEquals("FAILED",c.getStatus());assertNotNull(c.getFailureReason());assertNull(c.getOperationToken());
 }
 @Test void pendingInvitationRevokedUsingStoredIdentityAfterAccountDeletion()throws Exception{
  when(access.isTeam(1,3L)).thenReturn(false);when(em.find(MemberGithubAccount.class,3L)).thenReturn(null);
  when(client.request(5,"GET","/users/alice",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":9}")));
  when(client.request(eq(5L),eq("DELETE"),anyString(),isNull())).thenReturn(new GithubAppClient.Reply(404,mapper.nullNode()));
  service.handleRevoke(job("COLLAB_REVOKE",2,88L));verify(client).request(5,"DELETE","/repos/org/repo/collaborators/alice",null);verify(client).request(5,"DELETE","/repos/org/repo/invitations/88",null);assertEquals("NOT_INVITED",c.getStatus());
 }
 @Test void concurrentOperationDoesNotMakeNetworkCall()throws Exception{c.setLeaseUntil(java.time.Instant.now().plusSeconds(100));assertThrows(IllegalStateException.class,()->service.handleInvite(job("COLLAB_INVITE",2,null)));verifyNoInteractions(client);}
 @Test void acceptedInvitationIsPersistedAndCompletedJobIsIdempotent()throws Exception{
  when(client.request(5,"GET","/users/alice",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":9}")));
  when(client.request(eq(5L),eq("PUT"),eq("/repos/org/repo/collaborators/alice"),any())).thenReturn(new GithubAppClient.Reply(201,mapper.readTree("{\"id\":88}")));
  service.handleInvite(job("COLLAB_INVITE",2,null));assertEquals("INVITED",c.getStatus());assertEquals(88L,c.getInvitationId());assertNotNull(c.getInvitedAt());
  service.handleInvite(job("COLLAB_INVITE",2,null));verify(client,times(1)).request(eq(5L),eq("PUT"),anyString(),any());
 }
 @Test void alreadyCollaboratingBecomesActive()throws Exception{
  when(client.request(5,"GET","/users/alice",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":9}")));
  when(client.request(eq(5L),eq("PUT"),anyString(),any())).thenReturn(new GithubAppClient.Reply(204,mapper.nullNode()));
  service.handleInvite(job("COLLAB_INVITE",2,null));assertEquals("ACTIVE",c.getStatus());assertNull(c.getInvitationId());
 }
 @Test void disabledAutoInviteDoesNotCallGithub()throws Exception{link.setAutoInviteOnApproval(false);service.handleInvite(job("COLLAB_INVITE",2,null));verifyNoInteractions(client);}
 @Test void disabledAutoRevokeDoesNotCallGithub()throws Exception{link.setAutoRevokeOnRemoval(false);when(access.isTeam(1,3L)).thenReturn(false);service.handleRevoke(job("COLLAB_REVOKE",2,null));verifyNoInteractions(client);}
 @Test void leaderWhoAcquiredFormerMembersIdentityIsNeverRevoked()throws Exception{
  when(access.isTeam(1,3L)).thenReturn(false);Member leader=mock(Member.class);when(leader.getId()).thenReturn(7L);when(project.getCreator()).thenReturn(leader);when(em.find(MemberGithubAccount.class,7L)).thenReturn(account);
  service.handleRevoke(job("COLLAB_REVOKE",2,null));verifyNoInteractions(client);
 }
 @Test void crashLostInvitationIdIsReconciledOnlyByNumericIdentity()throws Exception{
  when(access.isTeam(1,3L)).thenReturn(false);when(client.request(5,"GET","/users/alice",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":9}")));
  when(client.request(eq(5L),eq("DELETE"),anyString(),isNull())).thenReturn(new GithubAppClient.Reply(204,mapper.nullNode()));
  when(client.paginate(5,"/repos/org/repo/invitations",null)).thenReturn(List.of(mapper.readTree("{\"id\":88,\"invitee\":{\"id\":9}}"),mapper.readTree("{\"id\":99,\"invitee\":{\"id\":999}}")));
  service.handleRevoke(job("COLLAB_REVOKE",2,null));verify(client).request(5,"DELETE","/repos/org/repo/invitations/88",null);verify(client,never()).request(5,"DELETE","/repos/org/repo/invitations/99",null);
 }
 @Test void compensationRunsAfterRepositoryUnlink()throws Exception{
  link.clearRepository();when(access.isTeam(1,3L)).thenReturn(false);
  when(client.request(5,"GET","/users/alice",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":9}")));
  when(client.request(eq(5L),eq("DELETE"),anyString(),isNull())).thenReturn(new GithubAppClient.Reply(204,mapper.nullNode()));
  var s=new GithubCollaborationService.Snapshot(1,3,2,5,"org/repo",9,"alice",88L,"COMPENSATION",2L);
  service.handleRevoke(new GithubWorkHandler.Job(2,"COLLAB_REVOKE",1L,3L,2,mapper.writeValueAsString(s)));verify(client).request(5,"DELETE","/repos/org/repo/collaborators/alice",null);
 }
 @Test void oldIdentityHistorySurvivesNewIdentityGrant()throws Exception{
  var old=new GithubCollaboratorGrant(new GithubCollaborationService.Snapshot(1,3,2,5,"org/repo",8,"old",77L));old.setStatus("INVITED");history.add(old);
  when(client.request(5,"GET","/users/alice",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":9}")));
  when(client.request(eq(5L),eq("PUT"),anyString(),any())).thenReturn(new GithubAppClient.Reply(204,mapper.nullNode()));
  service.handleInvite(job("COLLAB_INVITE",2,null));assertEquals(2,history.size());assertEquals(77L,old.getInvitationId());
  when(access.isTeam(1,3L)).thenReturn(false);service.enqueueRemoval(1,3);verify(queue,times(2)).enqueue(eq("COLLAB_REVOKE"),eq(1L),eq(3L),eq(2L),anyString(),isNull());
 }
 @Test void manualInviteStillWorksWhenAutomaticInvitesAreDisabled()throws Exception{
  link.setAutoInviteOnApproval(false);when(client.request(5,"GET","/users/alice",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":9}")));
  when(client.request(eq(5L),eq("PUT"),anyString(),any())).thenReturn(new GithubAppClient.Reply(204,mapper.nullNode()));
  var s=new GithubCollaborationService.Snapshot(1,3,2,5,"org/repo",9,"alice",null,"MANUAL",2L);service.handleInvite(new GithubWorkHandler.Job(2,"COLLAB_INVITE",1L,3L,2,mapper.writeValueAsString(s)));assertEquals("ACTIVE",c.getStatus());
 }
 @Test void compensationProtectsCurrentSameRepositoryReboundMembership()throws Exception{
  link.setBindingVersion(3);var s=new GithubCollaborationService.Snapshot(1,3,2,5,"org/repo",9,"alice",88L,"COMPENSATION",2L);service.handleRevoke(new GithubWorkHandler.Job(2,"COLLAB_REVOKE",1L,3L,2,mapper.writeValueAsString(s)));verifyNoInteractions(client);
 }
 @Test void manualInviteUsesExactRepositoryNotLinkedError(){when(access.lockLeader(1,7L)).thenReturn(project);link.clearRepository();var e=assertThrows(com.wardk.meeteam_backend.global.exception.CustomException.class,()->service.invite(1,7L,3));assertEquals(com.wardk.meeteam_backend.global.response.ErrorCode.GITHUB_REPOSITORY_NOT_LINKED,e.getErrorCode());}
 @Test void manualInviteUsesExactAccountNotLinkedError(){when(access.lockLeader(1,7L)).thenReturn(project);when(em.find(MemberGithubAccount.class,3L)).thenReturn(null);var e=assertThrows(com.wardk.meeteam_backend.global.exception.CustomException.class,()->service.invite(1,7L,3));assertEquals(com.wardk.meeteam_backend.global.response.ErrorCode.GITHUB_ACCOUNT_NOT_LINKED,e.getErrorCode());}
 @Test void manualInviteRejectsNonTeamWithDomainBadRequest(){when(access.lockLeader(1,7L)).thenReturn(project);when(access.isTeam(1,3L)).thenReturn(false);var e=assertThrows(com.wardk.meeteam_backend.global.exception.CustomException.class,()->service.invite(1,7L,3));assertEquals(com.wardk.meeteam_backend.global.response.ErrorCode.GITHUB_INVALID_REQUEST,e.getErrorCode());}
 @Test void automaticEnqueueSkipsAlreadyInvitedIdentity(){when(account.getLogin()).thenReturn("alice");c.setStatus("INVITED");service.enqueueInvite(1,3);verifyNoInteractions(queue);}
 @Test void automaticWorkerSkipsAlreadyActiveIdentity()throws Exception{c.setStatus("ACTIVE");service.handleInvite(job("COLLAB_INVITE",2,null));verifyNoInteractions(client);}
 @Test @SuppressWarnings("unchecked") void reconnectDoesNotReinviteIdentityWithExistingRecord(){TypedQuery<Long> ids=mock(TypedQuery.class);when(em.createQuery(anyString(),eq(Long.class))).thenReturn(ids);when(ids.setParameter(anyString(),any())).thenReturn(ids);when(ids.getResultList()).thenReturn(List.of(1L));service.accountLinked(3);verifyNoInteractions(queue);}
 @SuppressWarnings("unchecked") Member leaderRoster(boolean hasRecord){
  Member leader=mock(Member.class);when(leader.getId()).thenReturn(3L);when(project.getId()).thenReturn(1L);when(project.getCreator()).thenReturn(leader);when(project.isLeader(3L)).thenReturn(true);when(access.requireLeader(1,3L)).thenReturn(project);when(account.getLogin()).thenReturn("alice");
  TypedQuery<Member> roster=mock(TypedQuery.class);when(em.createQuery(anyString(),eq(Member.class))).thenReturn(roster);when(roster.setParameter(anyString(),any())).thenReturn(roster);when(roster.getResultList()).thenReturn(List.of());
  TypedQuery<GithubCollaborator> rows=em.createQuery("select c from GithubCollaborator c where c.projectId=:p and c.memberId=:m",GithubCollaborator.class);when(rows.getResultList()).thenReturn(hasRecord?List.of(c):List.of());return leader;
 }
 @Test void leaderRequiresCapturedNumericInstallerProof(){leaderRoster(false);link.setInstalledByMemberId(3L);assertEquals("NOT_INVITED",service.list(1,3L).get(0).accessStatus());link.setInstalledGithubUserId(9L);assertEquals("ACTIVE",service.list(1,3L).get(0).accessStatus());}
 @Test void relinkedLeaderIdentityDoesNotInheritInstallerAccess(){leaderRoster(false);link.setInstalledByMemberId(3L);link.setInstalledGithubUserId(9L);when(account.getGithubUserId()).thenReturn(10L);assertEquals("NOT_INVITED",service.list(1,3L).get(0).accessStatus());}
 @Test void knownRevocationOverridesInstallerProof(){leaderRoster(true);link.setInstalledByMemberId(3L);link.setInstalledGithubUserId(9L);c.setStatus("NOT_INVITED");assertEquals("NOT_INVITED",service.list(1,3L).get(0).accessStatus());}
 GithubWorkHandler.Job numericJob(String kind,String mode)throws Exception{return new GithubWorkHandler.Job(4,kind,1L,3L,2,mapper.writeValueAsString(new GithubCollaborationService.Snapshot(1,3,2,5,"org/old",9,"alice",88L,mode,2L,10L)));}
 void canonicalReply()throws Exception{when(client.request(5,"GET","/repositories/10",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":10,\"full_name\":\"newowner/renamed\"}")));when(client.request(5,"GET","/users/alice",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":9}")));}
 @Test void queuedInviteSurvivesRepositoryRenameAndUsesCanonicalNumericTarget()throws Exception{
  link.setFullName("newowner/renamed");canonicalReply();when(client.request(eq(5L),eq("PUT"),anyString(),any())).thenReturn(new GithubAppClient.Reply(204,mapper.nullNode()));
  service.handleInvite(numericJob("COLLAB_INVITE","AUTO"));verify(client).request(eq(5L),eq("PUT"),eq("/repos/newowner/renamed/collaborators/alice"),any());assertEquals(10L,history.get(0).getRepositoryId());
 }
 @Test void retainedGrantRemovalResolvesTransferredRepositoryByNumericId()throws Exception{
  link.setFullName("newowner/renamed");when(access.isTeam(1,3L)).thenReturn(false);canonicalReply();when(client.request(eq(5L),eq("DELETE"),anyString(),isNull())).thenReturn(new GithubAppClient.Reply(204,mapper.nullNode()));
  service.handleRevoke(numericJob("COLLAB_REVOKE","AUTO_REVOKE"));verify(client).request(5,"DELETE","/repos/newowner/renamed/collaborators/alice",null);verify(client).paginate(5,"/repos/newowner/renamed/invitations",null);
 }
 @Test @SuppressWarnings("unchecked") void compensationProtectsLeaderOfDifferentProjectBoundToSameNumericRepository()throws Exception{
  link.clearRepository();GithubProjectLink other=new GithubProjectLink(2);other.setInstallationActive(true);other.setInstallationId(5L);other.setGithubRepositoryId(10L);other.setFullName("newowner/renamed");
  TypedQuery<GithubProjectLink> links=em.createQuery("links",GithubProjectLink.class);when(links.getResultList()).thenReturn(List.of(other));Project newProject=mock(Project.class);Member leader=mock(Member.class);when(leader.getId()).thenReturn(7L);when(newProject.getCreator()).thenReturn(leader);when(em.find(Project.class,2L)).thenReturn(newProject);when(em.find(MemberGithubAccount.class,7L)).thenReturn(account);
  service.handleRevoke(numericJob("COLLAB_REVOKE","COMPENSATION"));verifyNoInteractions(client);
 }
 @Test @SuppressWarnings("unchecked") void compensationProtectsCurrentTeamOfDifferentProjectBoundToSameNumericRepository()throws Exception{
  link.clearRepository();GithubProjectLink other=new GithubProjectLink(2);other.setInstallationActive(true);other.setInstallationId(5L);other.setGithubRepositoryId(10L);other.setFullName("newowner/renamed");
  TypedQuery<GithubProjectLink> links=em.createQuery("links",GithubProjectLink.class);when(links.getResultList()).thenReturn(List.of(other));when(em.find(Project.class,2L)).thenReturn(mock(Project.class));
  MemberGithubAccount transferred=mock(MemberGithubAccount.class);when(transferred.getMemberId()).thenReturn(4L);TypedQuery<MemberGithubAccount> accounts=em.createQuery("accounts",MemberGithubAccount.class);when(accounts.getResultList()).thenReturn(List.of(transferred));when(access.isTeam(2,4L)).thenReturn(true);
  service.handleRevoke(numericJob("COLLAB_REVOKE","COMPENSATION"));verifyNoInteractions(client);
 }
 @Test @SuppressWarnings("unchecked") void compensationStillRunsAfterOriginalProjectHardDeletion()throws Exception{
  when(em.find(Project.class,1L)).thenReturn(null);when(em.find(GithubProjectLink.class,1L)).thenReturn(null);when(em.find(GithubProjectLink.class,1L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);TypedQuery<GithubProjectLink> links=em.createQuery("links",GithubProjectLink.class);when(links.getResultList()).thenReturn(List.of());
  canonicalReply();when(client.request(eq(5L),eq("DELETE"),anyString(),isNull())).thenReturn(new GithubAppClient.Reply(204,mapper.nullNode()));service.handleRevoke(numericJob("COLLAB_REVOKE","COMPENSATION"));verify(client).request(5,"DELETE","/repos/newowner/renamed/collaborators/alice",null);
 }
 @Test void numericRepositoryMismatchNeverMutatesAnotherRepository()throws Exception{
  when(client.request(5,"GET","/repositories/10",null)).thenReturn(new GithubAppClient.Reply(200,mapper.readTree("{\"id\":999,\"full_name\":\"org/reused\"}")));assertThrows(IllegalStateException.class,()->service.handleInvite(numericJob("COLLAB_INVITE","AUTO")));verify(client,never()).request(anyLong(),eq("PUT"),anyString(),any());
 }
 @Test void githubStrategiesReuseProjectPayloadWithoutActor(){
  var context=NotificationContext.builder().projectId(1L).projectName("team").receiverId(3L).build();
  for(NotificationPayloadStrategy strategy:List.of(new GithubRepoInvitedStrategy(),new GithubReviewRequestedStrategy(),new GithubPrMergedStrategy())){assertFalse(strategy.getType().requiresActor());var payload=(ProjectEndedPayload)strategy.create(context);assertEquals(1L,payload.getProjectId());assertEquals("team",payload.getProjectName());}
 }
}
