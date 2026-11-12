package com.wardk.meeteam_backend.domain.github.collaboration;

import com.fasterxml.jackson.databind.*;
import com.wardk.meeteam_backend.domain.github.integration.*;
import com.wardk.meeteam_backend.domain.github.account.*;
import com.wardk.meeteam_backend.domain.github.repository.GithubAppClient;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.notification.*;
import com.wardk.meeteam_backend.domain.notification.entity.NotificationType;
import com.wardk.meeteam_backend.domain.notification.service.NotificationSaveService;
import com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver;
import com.wardk.meeteam_backend.web.github.collaboration.GithubCollaboratorResponse;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import java.time.*;
import java.util.*;

@Service
public class GithubCollaborationService {
 private final EntityManager em; private final GithubProjectAccess access; private final GithubAppClient client;
 private final GithubWorkQueue queue; private final ObjectMapper mapper; private final NotificationSaveService notifications;
 private final ApplicationEventPublisher publisher; private final S3ImageUrlResolver images; private final TransactionTemplate tx;
 public GithubCollaborationService(EntityManager em,GithubProjectAccess access,GithubAppClient client,GithubWorkQueue queue,ObjectMapper mapper,NotificationSaveService notifications,ApplicationEventPublisher publisher,S3ImageUrlResolver images,PlatformTransactionManager manager){this.em=em;this.access=access;this.client=client;this.queue=queue;this.mapper=mapper;this.notifications=notifications;this.publisher=publisher;this.images=images;this.tx=new TransactionTemplate(manager);}
 public record Snapshot(long projectId,long memberId,long bindingVersion,long installationId,String repository,long githubUserId,String login,Long invitationId,String mode,Long removalBindingVersion,Long repositoryId) {
  public Snapshot(long p,long m,long b,long i,String r,long g,String l,Long invitation,String mode,Long removalBindingVersion){this(p,m,b,i,r,g,l,invitation,mode,removalBindingVersion,null);}
  public Snapshot(long p,long m,long b,long i,String r,long g,String l,Long invitation){this(p,m,b,i,r,g,l,invitation,"AUTO",b,null);}
  Snapshot withMode(String mode){return new Snapshot(projectId,memberId,bindingVersion,installationId,repository,githubUserId,login,invitationId,mode,removalBindingVersion,repositoryId);}
 }
 private record Operation(Snapshot snapshot,String token) {}
 public List<GithubCollaboratorResponse> list(long projectId,Long actor){return tx.execute(t->{Project p=access.requireLeader(projectId,actor);return roster(p).stream().map(m->response(p,m)).toList();});}
 public GithubCollaboratorResponse invite(long projectId,Long actor,long memberId){
  Snapshot s=tx.execute(t->{Project p=access.lockLeader(projectId,actor);if(p.isLeader(memberId)||!access.isTeam(projectId,memberId))throw new CustomException(ErrorCode.GITHUB_INVALID_REQUEST);GithubProjectLink l=link(projectId);if(l==null||!l.hasRepository())throw new CustomException(ErrorCode.GITHUB_REPOSITORY_NOT_LINKED);MemberGithubAccount a=em.find(MemberGithubAccount.class,memberId);if(a==null)throw new CustomException(ErrorCode.GITHUB_ACCOUNT_NOT_LINKED);return snapshot(l,memberId,a.getGithubUserId(),a.getLogin(),null).withMode("MANUAL");});
  executeInvite(s,null);
  return tx.execute(t->response(em.find(Project.class,projectId),em.find(Member.class,memberId)));
 }
 public void enqueueInvite(long projectId,long memberId){
  GithubProjectLink l=link(projectId);Project p=em.find(Project.class,projectId);MemberGithubAccount a=em.find(MemberGithubAccount.class,memberId);
  if(p==null||p.isLeader(memberId)||!access.isTeam(projectId,memberId)||l==null||!l.hasRepository()||!l.isAutoInviteOnApproval()||a==null)return;
  Snapshot target=snapshot(l,memberId,a.getGithubUserId(),a.getLogin(),null);if(alreadyGranted(target))return;
  enqueue("COLLAB_INVITE",target);
 }
 public void enqueueRemoval(long projectId,long memberId){enqueueRemoval(projectId,memberId,null,null);}
 public void enqueueRemoval(long projectId,long memberId,Long capturedGithubId,String capturedLogin){
  GithubProjectLink l=link(projectId);Project p=em.find(Project.class,projectId);if(p==null||p.isLeader(memberId)||l==null||!l.hasRepository()||!l.isAutoRevokeOnRemoval())return;
  Set<Long> identities=new HashSet<>();
  for(GithubCollaboratorGrant g:grants(projectId,memberId)){
   identities.add(g.getGithubUserId());
   enqueue("COLLAB_REVOKE",new Snapshot(projectId,memberId,g.getBindingVersion(),g.getInstallationId(),g.getRepository(),g.getGithubUserId(),g.getLogin(),g.getInvitationId(),"AUTO_REVOKE",l.getBindingVersion(),g.getRepositoryId()));
  }
  GithubCollaborator c=collaborator(projectId,memberId,false);MemberGithubAccount a=em.find(MemberGithubAccount.class,memberId);
  if(c!=null&&c.getBindingVersion()==l.getBindingVersion()&&c.getGithubUserId()>0&&identities.add(c.getGithubUserId()))enqueue("COLLAB_REVOKE",snapshot(l,memberId,c.getGithubUserId(),c.getLogin(),c.getInvitationId()));
  if(a!=null&&identities.add(a.getGithubUserId()))enqueue("COLLAB_REVOKE",snapshot(l,memberId,a.getGithubUserId(),a.getLogin(),null));
  if(capturedGithubId!=null&&capturedLogin!=null&&identities.add(capturedGithubId))enqueue("COLLAB_REVOKE",snapshot(l,memberId,capturedGithubId,capturedLogin,null));
 }
 public void accountLinked(long memberId){
  List<Long> ids=em.createQuery("select distinct l.projectId from GithubProjectLink l, Project p where l.projectId=p.id and l.installationActive=true and l.githubRepositoryId is not null and (p.creator.id=:m or exists (select pm.id from ProjectMember pm where pm.project.id=p.id and pm.member.id=:m))",Long.class).setParameter("m",memberId).getResultList();
  MemberGithubAccount account=em.find(MemberGithubAccount.class,memberId);if(account==null)return;
  for(Long id:ids){GithubProjectLink l=link(id);if(l!=null&&l.hasRepository()){Snapshot target=snapshot(l,memberId,account.getGithubUserId(),account.getLogin(),null);if(!hasRecorded(target))enqueueInvite(id,memberId);}}
 }
 public void repositoryLinked(long projectId){Project p=em.find(Project.class,projectId);if(p!=null)for(Member m:roster(p))enqueueInvite(projectId,m.getId());}
 public void projectDeleted(long projectId){em.createQuery("delete from GithubCollaboratorGrant g where g.projectId=:p").setParameter("p",projectId).executeUpdate();em.createQuery("delete from GithubCollaborator c where c.projectId=:p").setParameter("p",projectId).executeUpdate();}
 public void handleInvite(GithubWorkHandler.Job job){executeInvite(decode(job.payload()),job.id());}
 public void handleRevoke(GithubWorkHandler.Job job){executeRevoke(decode(job.payload()),job.id());}
 private void executeInvite(Snapshot s,Long workId){
  Operation op=tx.execute(t->begin(s,true,workId));if(op==null)return;
  try{
   Snapshot target=canonical(s);verify(s);var r=client.request(s.installationId(),"PUT",repo(target)+"/collaborators/"+s.login(),mapper.createObjectNode().put("permission","push"));
   if(r.status()!=201&&r.status()!=204)throw new IllegalStateException("GitHub 초대 실패");
   Long invitation=r.status()==201?r.body().path("id").asLong(0):null;if(invitation!=null&&invitation<=0)throw new IllegalStateException("GitHub 초대 실패");
   tx.executeWithoutResult(t->{GithubCollaborator c=owned(op);if(c==null)return;finish(c);GithubCollaboratorGrant g=grant(s,true);if(workId!=null)g.setLastCompletedWorkId(workId);g.setInvitationId(invitation);g.setInvitedAt(Instant.now());g.setStatus(r.status()==201?"INVITED":"ACTIVE");g.setFailureReason(null);c.setInvitationId(invitation);c.setInvitedAt(Instant.now());c.setStatus(r.status()==201?"INVITED":"ACTIVE");c.setFailureReason(null);
    if(!valid(s)||!access.isTeam(s.projectId(),s.memberId())||!currentIdentity(s))enqueue("COLLAB_REVOKE",new Snapshot(s.projectId(),s.memberId(),s.bindingVersion(),s.installationId(),s.repository(),s.githubUserId(),s.login(),invitation,"COMPENSATION",s.bindingVersion(),s.repositoryId()));
    else notifyMember(s.projectId(),s.memberId(),NotificationType.GITHUB_REPO_INVITED);
   });
  }catch(RuntimeException e){failed(op);throw e;}
 }
 private void executeRevoke(Snapshot s,Long workId){
  Operation op=tx.execute(t->begin(s,false,workId));if(op==null)return;
  try{
   Snapshot target=canonical(s);verify(s);
   if(Boolean.TRUE.equals(tx.execute(t->protectedIdentity(s)))){tx.executeWithoutResult(t->{GithubCollaborator c=owned(op);if(c!=null)finish(c);});return;}
   var result=client.request(s.installationId(),"DELETE",repo(target)+"/collaborators/"+s.login(),null);requireDelete(result.status());
   // A process may have died after GitHub accepted PUT and before storing invitationId.
   // Numeric invitee identity, never login alone, controls this reconciliation.
   Set<Long> invitations=new HashSet<>();if(s.invitationId()!=null)invitations.add(s.invitationId());
   for(JsonNode invitation:client.paginate(s.installationId(),repo(target)+"/invitations",null))if(invitation.path("invitee").path("id").asLong()==s.githubUserId()&&invitation.path("id").asLong()>0)invitations.add(invitation.path("id").asLong());
   for(Long id:invitations){if(Boolean.TRUE.equals(tx.execute(t->protectedIdentity(s))))break;requireDelete(client.request(s.installationId(),"DELETE",repo(target)+"/invitations/"+id,null).status());}
   tx.executeWithoutResult(t->{GithubCollaborator c=owned(op);if(c==null)return;finish(c);GithubCollaboratorGrant g=grant(s,true);if(workId!=null)g.setLastCompletedWorkId(workId);g.setStatus("NOT_INVITED");g.setInvitationId(null);g.setFailureReason(null);c.setStatus("NOT_INVITED");c.setInvitationId(null);c.setFailureReason(null);
    if(valid(s)&&access.isTeam(s.projectId(),s.memberId())){MemberGithubAccount a=em.find(MemberGithubAccount.class,s.memberId());GithubProjectLink l=link(s.projectId());if(a!=null)enqueue("COLLAB_INVITE",snapshot(l,s.memberId(),a.getGithubUserId(),a.getLogin(),null).withMode("RECONCILE"));}
   });
  }catch(RuntimeException e){failed(op);throw e;}
 }
 private Operation begin(Snapshot s,boolean invite,Long workId){
  GithubProjectLink l=em.find(GithubProjectLink.class,s.projectId(),LockModeType.PESSIMISTIC_WRITE);
  Project p=em.find(Project.class,s.projectId());if((invite&&p==null)||(p!=null&&p.isLeader(s.memberId())))return null;
  boolean compensation="COMPENSATION".equals(s.mode());
  if(invite){if(!valid(s)||!access.isTeam(s.projectId(),s.memberId())||!currentIdentity(s))return null;if(!"MANUAL".equals(s.mode())&&!"RECONCILE".equals(s.mode())&&!l.isAutoInviteOnApproval())return null;}
  else {
   if(!compensation&&(l==null||!l.hasRepository()||l.getBindingVersion()!=(s.removalBindingVersion()==null?s.bindingVersion():s.removalBindingVersion())||!l.isAutoRevokeOnRemoval()))return null;
   if(protectedIdentity(s))return null;
  }
  // The row serializes all identities and repository generations for this membership.
  if(l==null&&p!=null)em.lock(p,LockModeType.PESSIMISTIC_WRITE);
  GithubCollaborator c=collaborator(s.projectId(),s.memberId(),true);
  if(l!=null&&l.hasRepository()&&c.getGithubUserId()>0&&c.getLogin()!=null&&c.getBindingVersion()==l.getBindingVersion()){
   Snapshot previous=snapshot(l,s.memberId(),c.getGithubUserId(),c.getLogin(),c.getInvitationId());
   if(grant(previous,false)==null){GithubCollaboratorGrant retained=grant(previous,true);retained.setStatus(c.getStatus());retained.setInvitedAt(c.getInvitedAt());retained.setFailureReason(c.getFailureReason());}
  }
  GithubCollaboratorGrant g=grant(s,true);
  if(invite&&!"MANUAL".equals(s.mode())&&!"RECONCILE".equals(s.mode())&&("ACTIVE".equals(g.getStatus())||"INVITED".equals(g.getStatus())))return null;
  if(workId!=null&&g.getLastCompletedWorkId()!=null&&g.getLastCompletedWorkId()>=workId)return null;
  if(c.getLeaseUntil()!=null&&c.getLeaseUntil().isAfter(Instant.now()))throw new IllegalStateException("GitHub 권한 변경 처리 중입니다.");
  String token=UUID.randomUUID().toString();c.setOperationToken(token);c.setLeaseUntil(Instant.now().plusSeconds(120));c.setBindingVersion(s.bindingVersion());c.setGithubUserId(s.githubUserId());c.setLogin(s.login());return new Operation(s,token);
 }
 private boolean protectedIdentity(Snapshot s){
  Project original=em.find(Project.class,s.projectId());
  if(original!=null&&leaderIdentity(original,s.githubUserId()))return true;
  // A repository may have been rebound to an entirely different MeeTeam project.
  // Destructive compensation must honor every current delegation of the stable repository.
  List<GithubProjectLink> links=s.repositoryId()!=null?
   em.createQuery("select l from GithubProjectLink l where l.githubRepositoryId=:repository",GithubProjectLink.class).setParameter("repository",s.repositoryId()).getResultList():
   em.createQuery("select l from GithubProjectLink l where l.fullName=:repository",GithubProjectLink.class).setParameter("repository",s.repository()).getResultList();
  List<MemberGithubAccount> identities=em.createQuery("select a from MemberGithubAccount a where a.githubUserId=:id",MemberGithubAccount.class).setParameter("id",s.githubUserId()).getResultList();
  for(GithubProjectLink l:links){
   if(!l.hasRepository())continue;Project p=em.find(Project.class,l.getProjectId());if(p==null)continue;
   if(leaderIdentity(p,s.githubUserId()))return true;
   for(MemberGithubAccount a:identities)if(access.isTeam(l.getProjectId(),a.getMemberId()))return true;
   if(l.getProjectId()==s.projectId()&&access.isTeam(s.projectId(),s.memberId())&&currentIdentity(s))return true;
  }
  return false;
 }
 private boolean leaderIdentity(Project p,long githubUserId){if(p.getCreator()==null)return false;MemberGithubAccount leader=em.find(MemberGithubAccount.class,p.getCreator().getId());return leader!=null&&leader.getGithubUserId()==githubUserId;}
 private List<GithubCollaboratorGrant> grants(long projectId,long memberId){return em.createQuery("select g from GithubCollaboratorGrant g where g.projectId=:p and g.memberId=:m",GithubCollaboratorGrant.class).setParameter("p",projectId).setParameter("m",memberId).getResultList();}
 private boolean hasRecorded(Snapshot s){if(grant(s,false)!=null)return true;GithubCollaborator c=collaborator(s.projectId(),s.memberId(),false);return c!=null&&c.getBindingVersion()==s.bindingVersion()&&c.getGithubUserId()==s.githubUserId();}
 private boolean alreadyGranted(Snapshot s){GithubCollaboratorGrant g=grant(s,false);if(g!=null)return "ACTIVE".equals(g.getStatus())||"INVITED".equals(g.getStatus());GithubCollaborator c=collaborator(s.projectId(),s.memberId(),false);return c!=null&&c.getBindingVersion()==s.bindingVersion()&&c.getGithubUserId()==s.githubUserId()&&("ACTIVE".equals(c.getStatus())||"INVITED".equals(c.getStatus()));}
 private GithubCollaboratorGrant grant(Snapshot s,boolean create){
  for(GithubCollaboratorGrant g:grants(s.projectId(),s.memberId()))if(g.getBindingVersion()==s.bindingVersion()&&g.getGithubUserId()==s.githubUserId())return g;
  if(!create)return null;GithubCollaboratorGrant g=new GithubCollaboratorGrant(s);em.persist(g);return g;
 }
 private GithubCollaborator owned(Operation op){GithubCollaborator c=collaborator(op.snapshot().projectId(),op.snapshot().memberId(),false);return c!=null&&op.token().equals(c.getOperationToken())?c:null;}
 private void finish(GithubCollaborator c){c.setOperationToken(null);c.setLeaseUntil(null);}
 private void failed(Operation op){tx.executeWithoutResult(t->{GithubCollaborator c=owned(op);if(c!=null){finish(c);c.setStatus("FAILED");c.setFailureReason("GitHub 저장소 권한 변경에 실패했습니다. 다시 시도해 주세요.");GithubCollaboratorGrant g=grant(op.snapshot(),true);g.setStatus("FAILED");g.setFailureReason(c.getFailureReason());}});}
 private void verify(Snapshot s){if(s.login()==null||!s.login().matches("[A-Za-z0-9-]{1,39}"))throw new IllegalStateException("GitHub 계정을 확인할 수 없습니다.");var r=client.request(s.installationId(),"GET","/users/"+s.login(),null);if(r.status()!=200||r.body().path("id").asLong()!=s.githubUserId())throw new IllegalStateException("GitHub 계정을 확인할 수 없습니다.");}
 private static void requireDelete(int status){if(status!=204&&status!=404)throw new IllegalStateException("GitHub 권한 회수 실패");}
 private Snapshot canonical(Snapshot s){
  if(s.repositoryId()==null)return s;
  var reply=client.request(s.installationId(),"GET","/repositories/"+s.repositoryId(),null);
  String name=reply.body().path("full_name").asText();
  if(reply.status()!=200||reply.body().path("id").asLong()!=s.repositoryId()||!name.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))throw new IllegalStateException("저장소를 확인할 수 없습니다.");
  return new Snapshot(s.projectId(),s.memberId(),s.bindingVersion(),s.installationId(),name,s.githubUserId(),s.login(),s.invitationId(),s.mode(),s.removalBindingVersion(),s.repositoryId());
 }
 private String repo(Snapshot s){if(s.repository()==null||!s.repository().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))throw new IllegalStateException("저장소를 확인할 수 없습니다.");return "/repos/"+s.repository();}
 private boolean currentIdentity(Snapshot s){MemberGithubAccount a=em.find(MemberGithubAccount.class,s.memberId());return a!=null&&a.getGithubUserId()==s.githubUserId();}
 private boolean valid(Snapshot s){GithubProjectLink l=link(s.projectId());return l!=null&&l.hasRepository()&&l.getBindingVersion()==s.bindingVersion()&&Objects.equals(l.getInstallationId(),s.installationId())&&(s.repositoryId()!=null?Objects.equals(l.getGithubRepositoryId(),s.repositoryId()):Objects.equals(l.getFullName(),s.repository()));}
 private GithubProjectLink link(long projectId){return em.find(GithubProjectLink.class,projectId);}
 private Snapshot snapshot(GithubProjectLink l,long memberId,long githubId,String login,Long invitation){return new Snapshot(l.getProjectId(),memberId,l.getBindingVersion(),l.getInstallationId(),l.getFullName(),githubId,login,invitation,"AUTO",l.getBindingVersion(),l.getGithubRepositoryId());}
 private void enqueue(String kind,Snapshot s){try{queue.enqueue(kind,s.projectId(),s.memberId(),s.bindingVersion(),mapper.writeValueAsString(s),null);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("GitHub 작업 저장 실패");}}
 private Snapshot decode(String payload){try{return mapper.readValue(payload,Snapshot.class);}catch(Exception e){throw new IllegalArgumentException("GitHub 작업 정보 오류");}}
 private GithubCollaborator collaborator(long projectId,long memberId,boolean create){List<GithubCollaborator> rows=em.createQuery("select c from GithubCollaborator c where c.projectId=:p and c.memberId=:m",GithubCollaborator.class).setParameter("p",projectId).setParameter("m",memberId).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();if(!rows.isEmpty())return rows.get(0);if(!create)return null;GithubCollaborator c=new GithubCollaborator(projectId,memberId);em.persist(c);return c;}
 private List<Member> roster(Project p){Map<Long,Member> members=new LinkedHashMap<>();members.put(p.getCreator().getId(),p.getCreator());for(Member m:em.createQuery("select pm.member from ProjectMember pm where pm.project.id=:p",Member.class).setParameter("p",p.getId()).getResultList())members.put(m.getId(),m);return new ArrayList<>(members.values());}
 private GithubCollaboratorResponse response(Project p,Member m){MemberGithubAccount a=em.find(MemberGithubAccount.class,m.getId());GithubProjectLink l=link(p.getId());GithubCollaborator c=collaborator(p.getId(),m.getId(),false);String status="NOT_INVITED";
  GithubCollaboratorGrant current=null;if(a!=null&&l!=null&&l.hasRepository())current=grant(snapshot(l,m.getId(),a.getGithubUserId(),a.getLogin(),null),false);
  if(a==null)status="NO_GITHUB_ACCOUNT";else if(l!=null&&l.hasRepository()&&current!=null)status=current.getStatus();else if(l!=null&&l.hasRepository()&&c!=null&&c.getBindingVersion()==l.getBindingVersion()&&c.getGithubUserId()==a.getGithubUserId())status=c.getStatus();else if(l!=null&&l.hasRepository()&&p.isLeader(m.getId())&&Objects.equals(l.getInstalledByMemberId(),m.getId())&&Objects.equals(l.getInstalledGithubUserId(),a.getGithubUserId()))status="ACTIVE";
  // Installation alone is not proof that a leader owns or can access this repository.
  return new GithubCollaboratorResponse(m.getId(),m.getPublicName(),images.resolve(m.getStoreFileName()),p.isLeader(m.getId()),a==null?null:a.getLogin(),status,current!=null&&current.getInvitedAt()!=null?current.getInvitedAt().atOffset(ZoneOffset.ofHours(9)):c==null||c.getInvitedAt()==null?null:c.getInvitedAt().atOffset(ZoneOffset.ofHours(9)));}
 public void notifyGithub(long projectId,long githubUserId,String type,String title){NotificationType n=NotificationType.valueOf(type);if(n!=NotificationType.GITHUB_REVIEW_REQUESTED&&n!=NotificationType.GITHUB_PR_MERGED&&n!=NotificationType.GITHUB_REPO_INVITED)throw new IllegalArgumentException("지원하지 않는 알림입니다.");tx.executeWithoutResult(t->{for(MemberGithubAccount a:em.createQuery("select a from MemberGithubAccount a where a.githubUserId=:id",MemberGithubAccount.class).setParameter("id",githubUserId).getResultList())if(access.isTeam(projectId,a.getMemberId()))notifyMember(projectId,a.getMemberId(),n);});}
 private void notifyMember(long projectId,long memberId,NotificationType type){Project p=em.find(Project.class,projectId);Member m=em.find(Member.class,memberId);if(p==null||m==null||!access.isTeam(projectId,memberId))return;notifications.save(m,p,null,type,null);publisher.publishEvent(new NotificationEvent(memberId,projectId,null,type,null));}
 public void memberWebhook(long repositoryId,long githubUserId,String login,boolean added){tx.executeWithoutResult(t->{for(GithubProjectLink l:em.createQuery("select l from GithubProjectLink l where l.githubRepositoryId=:r",GithubProjectLink.class).setParameter("r",repositoryId).getResultList())for(MemberGithubAccount a:em.createQuery("select a from MemberGithubAccount a where a.githubUserId=:id",MemberGithubAccount.class).setParameter("id",githubUserId).getResultList()){if(!l.hasRepository()||!access.isTeam(l.getProjectId(),a.getMemberId()))continue;em.lock(l,LockModeType.PESSIMISTIC_WRITE);GithubCollaborator c=collaborator(l.getProjectId(),a.getMemberId(),true);c.setGithubUserId(githubUserId);c.setLogin(a.getLogin());c.setBindingVersion(l.getBindingVersion());c.setStatus(added?"ACTIVE":"NOT_INVITED");c.setInvitationId(null);c.setFailureReason(null);GithubCollaboratorGrant g=grant(snapshot(l,a.getMemberId(),githubUserId,a.getLogin(),null),true);g.setStatus(added?"ACTIVE":"NOT_INVITED");g.setInvitationId(null);g.setFailureReason(null);}});}
}
