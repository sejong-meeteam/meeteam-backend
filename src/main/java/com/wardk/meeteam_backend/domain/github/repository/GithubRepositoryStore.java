package com.wardk.meeteam_backend.domain.github.repository;
import com.fasterxml.jackson.databind.*;
import com.wardk.meeteam_backend.domain.github.integration.*;
import com.wardk.meeteam_backend.domain.github.account.MemberGithubAccount;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class GithubRepositoryStore {
 private final EntityManager em; private final GithubProjectAccess access; private final ObjectMapper mapper;
 private final GithubWorkQueue queue; private final ApplicationEventPublisher events;
 public record Context(long projectId,long memberId,long authVersion,long linkVersion,long githubUserId,long bindingVersion){}
 public Context context(long projectId,long memberId){
  access.lockLeader(projectId,memberId);var m=access.requireActive(memberId);var a=em.find(MemberGithubAccount.class,memberId);
  if(a==null)throw new CustomException(ErrorCode.GITHUB_ACCOUNT_NOT_LINKED);
  var l=em.find(GithubProjectLink.class,projectId);
  return new Context(projectId,memberId,m.getAuthVersion(),m.getGithubLinkVersion(),a.getGithubUserId(),l==null?0:l.getBindingVersion());
 }
 public void validate(Context c){if(!context(c.projectId(),c.memberId()).equals(c))throw GithubAppClient.invalid();}
 @Transactional(readOnly=true) public GithubProjectLink get(long p,long m){access.requireLeader(p,m);var l=em.find(GithubProjectLink.class,p);return l==null?new GithubProjectLink(p):l;}
 @Transactional(readOnly=true) public Set<Long> taken(long project){return new HashSet<>(em.createQuery("select l.githubRepositoryId from GithubProjectLink l where l.projectId<>:p and l.githubRepositoryId is not null",Long.class).setParameter("p",project).getResultList());}
 public void install(Context c,long installation,Set<Long> ids){
  validate(c);var l=em.find(GithubProjectLink.class,c.projectId());if(l==null){l=new GithubProjectLink(c.projectId());em.persist(l);}
  // Always invalidate the old binding, including when repository access was reduced on the same installation.
  l.clearRepository();l.setInstallationId(installation);l.setInstallationActive(true);l.setInstalledByMemberId(c.memberId());l.setInstalledGithubUserId(c.githubUserId());l.setInstalledAt(Instant.now());
  try{l.setAuthorizedRepositoryIds(mapper.writeValueAsString(ids));}catch(Exception e){throw GithubAppClient.invalid();}
 }
 public GithubProjectLink link(Context c,long installation,JsonNode repository){
  validate(c);var l=em.find(GithubProjectLink.class,c.projectId());requireInstalled(l);
  long id=repository.path("id").asLong();if(!Objects.equals(l.getInstallationId(),installation)||!allowed(l).contains(id))throw GithubAppClient.invalid();
  if(taken(c.projectId()).contains(id))throw new CustomException(ErrorCode.GITHUB_REPOSITORY_TAKEN);
  l.clearRepository();l.setGithubRepositoryId(id);l.setFullName(repository.path("full_name").asText());l.setHtmlUrl(repository.path("html_url").asText());l.setDescription(repository.path("description").asText(""));
  l.setRepositoryPrivate(repository.path("private").asBoolean());l.setDefaultBranch(repository.path("default_branch").asText());l.setStars(repository.path("stargazers_count").asInt());l.setForks(repository.path("forks_count").asInt());l.setLinkedAt(Instant.now());em.flush();
  queue.enqueue("SYNC",c.projectId(),null,l.getBindingVersion(),"{}","link-sync:"+c.projectId()+":"+l.getBindingVersion());events.publishEvent(new GithubDomainEvents.RepositoryLinked(c.projectId()));return l;
 }
 public GithubProjectLink unlink(long p,long m){access.lockLeader(p,m);var l=em.find(GithubProjectLink.class,p);if(l==null)return new GithubProjectLink(p);l.clearRepository();return l;}
 public GithubProjectLink settings(long p,long m,Boolean invite,Boolean revoke){access.lockLeader(p,m);var l=em.find(GithubProjectLink.class,p);if(l==null||!l.hasRepository())throw new CustomException(ErrorCode.GITHUB_REPOSITORY_NOT_LINKED);if(invite==null||revoke==null)throw GithubAppClient.invalid();l.setAutoInviteOnApproval(invite);l.setAutoRevokeOnRemoval(revoke);return l;}
 public Set<Long> allowed(GithubProjectLink l){try{Set<Long> ids=new HashSet<>();JsonNode a=mapper.readTree(l.getAuthorizedRepositoryIds());if(!a.isArray())throw new IllegalArgumentException();for(JsonNode n:a){if(!n.isIntegralNumber()||n.asLong()<=0)throw new IllegalArgumentException();ids.add(n.asLong());}return ids;}catch(Exception e){throw GithubAppClient.invalid();}}
 public static void requireInstalled(GithubProjectLink l){if(l==null||!l.isInstallationActive()||l.getInstallationId()==null)throw new CustomException(ErrorCode.GITHUB_APP_NOT_INSTALLED);}
}
