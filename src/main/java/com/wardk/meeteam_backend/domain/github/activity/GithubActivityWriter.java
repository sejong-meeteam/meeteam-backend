package com.wardk.meeteam_backend.domain.github.activity;
import com.fasterxml.jackson.databind.JsonNode;
import com.wardk.meeteam_backend.domain.github.integration.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
/** All writers serialize through the existing project link row. Event keys are canonical across backfill/webhooks. */
@Service @RequiredArgsConstructor @Transactional
public class GithubActivityWriter {
 private final EntityManager em; private final GithubWorkQueue queue;
 private GithubProjectLink lock(long repo){var rows=em.createQuery("select l from GithubProjectLink l where l.githubRepositoryId=:repo",GithubProjectLink.class).setParameter("repo",repo).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();return rows.isEmpty()?null:rows.get(0);}
 public void event(long repositoryId,String eventKey,String type,Long githubUserId,Integer number,String title,String url,Instant occurredAt){
  if(occurredAt==null||lock(repositoryId)==null)return;
  if(!Set.of("COMMIT","PR_OPENED","PR_MERGED","REVIEW","ISSUE_OPENED","ISSUE_CLOSED").contains(type))throw new IllegalArgumentException("Unsupported activity type");
  String key=repositoryId+":"+type+":"+eventKey;if(key.length()>190)throw new IllegalArgumentException("Event key too long");
  var existing=em.createQuery("select e from GithubActivityEvent e where e.dedupKey=:key",GithubActivityEvent.class).setParameter("key",key).getResultList();if(!existing.isEmpty()){if(existing.get(0).getGithubUserId()==null&&githubUserId!=null)existing.get(0).setGithubUserId(githubUserId);return;}
  var e=new GithubActivityEvent();e.setRepositoryId(repositoryId);e.setDedupKey(key);e.setType(type);e.setGithubUserId(githubUserId);e.setNumber(number);e.setTitle(limit(title,1024));e.setUrl(limit(url,1000));e.setOccurredAt(occurredAt);em.persist(e);
 }
 public void pullRequest(long repositoryId,JsonNode pr,String reviewDecision){if(lock(repositoryId)==null)return;int n=pr.path("number").asInt();if(n<=0)throw new IllegalArgumentException("Missing PR number");var rows=em.createQuery("select p from GithubPullRequest p where p.repositoryId=:repo and p.number=:n",GithubPullRequest.class).setParameter("repo",repositoryId).setParameter("n",n).getResultList();var p=rows.isEmpty()?new GithubPullRequest():rows.get(0);Instant updated=time(pr,"updated_at");if(p.getUpdatedAt()!=null&&updated!=null&&p.getUpdatedAt().isAfter(updated))return;p.setRepositoryId(repositoryId);p.setNumber(n);p.setGithubUserId(id(pr.path("user")));p.setAuthorLogin(pr.path("user").path("login").asText(""));p.setTitle(limit(pr.path("title").asText(""),1024));p.setUrl(limit(pr.path("html_url").asText(""),1000));p.setOpen("open".equals(pr.path("state").asText()));p.setDraft(pr.path("draft").asBoolean());if(reviewDecision!=null)p.setReviewDecision(reviewDecision);else if(p.getReviewDecision()==null)p.setReviewDecision("REVIEW_REQUIRED");p.setCreatedAt(time(pr,"created_at"));p.setUpdatedAt(updated);p.setMergedAt(time(pr,"merged_at"));if(rows.isEmpty())em.persist(p);}
 public void pushed(long repositoryId,Instant when){if(when==null||lock(repositoryId)==null)return;var s=em.find(GithubRepositorySnapshot.class,repositoryId);if(s==null){s=new GithubRepositorySnapshot(repositoryId);em.persist(s);}if(s.getLastPushedAt()==null||when.isAfter(s.getLastPushedAt()))s.setLastPushedAt(when);}
 public void requestSync(long repositoryId){var l=lock(repositoryId);if(l!=null&&l.hasRepository())queue.enqueue("SYNC",l.getProjectId(),null,l.getBindingVersion(),"{}",null);}
 public static Instant time(JsonNode n,String key){String value=n.path(key).asText(null);return value==null?null:Instant.parse(value);}
 public static Long id(JsonNode n){return n.path("id").isNumber()?n.path("id").longValue():null;}
 private static String limit(String s,int max){return s==null?"":s.substring(0,Math.min(s.length(),max));}
}
