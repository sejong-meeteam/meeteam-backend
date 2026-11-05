package com.wardk.meeteam_backend.domain.github.webhook;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.wardk.meeteam_backend.domain.github.integration.*;
import com.wardk.meeteam_backend.domain.github.activity.GithubActivityWriter;
import com.wardk.meeteam_backend.domain.github.collaboration.GithubCollaborationService;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
/** Receipt and all local effects commit together. No upstream calls under row locks. */
@Service
public class GithubWebhookProcessor {
 private final EntityManager em;private final ObjectMapper mapper;private final GithubActivityWriter activity;private final GithubCollaborationService collaboration;
 public GithubWebhookProcessor(EntityManager e,ObjectMapper m,GithubActivityWriter a,GithubCollaborationService c){em=e;mapper=m;activity=a;collaboration=c;}
 @Transactional public void apply(long workId,JsonNode n,Map<String,Long> authors){
  long installation=n.path("installation").path("id").asLong();if(installation<=0){receipt(workId);return;}
  // Installation row locks serialize deliveries and concurrent replay before checking receipt.
  var links=em.createQuery("select l from GithubProjectLink l where l.installationId=:id order by l.projectId",GithubProjectLink.class).setParameter("id",installation).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
  if(em.find(GithubWebhookReceipt.class,workId)!=null)return;
  String event=n.path("event").asText(),action=n.path("action").asText();
  if(event.equals("installation")){
   if(Set.of("deleted","suspend").contains(action))for(var l:links){l.setInstallationActive(false);l.setAuthorizedRepositoryIds("[]");l.clearRepository();}
  }else if(event.equals("installation_repositories")){
   Set<Long> removed=new HashSet<>();n.path("repositories_removed").forEach(r->removed.add(r.path("id").asLong()));
   for(var l:links){ArrayNode ids=mapper.createArrayNode();try{for(JsonNode id:mapper.readTree(l.getAuthorizedRepositoryIds()))if(!removed.contains(id.asLong()))ids.add(id.asLong());}catch(Exception ignored){throw new IllegalStateException("Invalid repository authorization metadata");}l.setAuthorizedRepositoryIds(ids.toString());if(removed.contains(l.getGithubRepositoryId()))l.clearRepository();}
  }else{
   long repo=n.path("repository").path("id").asLong();
   for(var l:links)if(l.hasRepository()&&Objects.equals(l.getGithubRepositoryId(),repo))repository(l,n,authors);
  }
  receipt(workId);
 }
 private void receipt(long id){if(em.find(GithubWebhookReceipt.class,id)==null){em.persist(new GithubWebhookReceipt(id));em.flush();}}
 private void repository(GithubProjectLink l,JsonNode n,Map<String,Long> authors){
  long repo=l.getGithubRepositoryId();String event=n.path("event").asText(),action=n.path("action").asText();Instant received=time(n,"received_at",Instant.EPOCH);
  switch(event){
   case "push" -> {
    if(!n.path("ref").asText().equals("refs/heads/"+l.getDefaultBranch()))return;
    Instant pushed=received;
    JsonNode pushedAt=n.path("repository").path("pushed_at");if(pushedAt.isIntegralNumber()&&pushedAt.canConvertToLong()&&pushedAt.longValue()>0){try{pushed=Instant.ofEpochSecond(pushedAt.longValue());}catch(java.time.DateTimeException ignored){/* Retain captured receive time. */}}
    for(JsonNode c:n.path("commits")){String sha=c.path("id").asText();if(sha.isBlank())continue;Instant at=time(c,"timestamp",received);activity.event(repo,"commit:"+sha,"COMMIT",authors.get(sha),null,null,null,at);}
    activity.pushed(repo,pushed);activity.requestSync(repo);
   }
   case "pull_request" -> {
    JsonNode pr=n.path("pull_request");int number=pr.path("number").asInt();if(number<=0)return;
    activity.pullRequest(repo,pr,null);activity.requestSync(repo);
    if(action.equals("opened"))activity.event(repo,"pr-opened:"+number,"PR_OPENED",id(pr.path("user")),number,title(pr),url(pr),time(pr,"created_at",received));
    if(action.equals("closed")&&pr.path("merged").asBoolean()){
     activity.event(repo,"pr-merged:"+number,"PR_MERGED",id(pr.path("user")),number,title(pr),url(pr),time(pr,"merged_at",received));
     Long author=id(pr.path("user"));if(author!=null)collaboration.notifyGithub(l.getProjectId(),author,"GITHUB_PR_MERGED",title(pr));
    }
    if(action.equals("review_requested")){Long reviewer=id(n.path("requested_reviewer"));if(reviewer!=null)collaboration.notifyGithub(l.getProjectId(),reviewer,"GITHUB_REVIEW_REQUESTED",title(pr));}
   }
   case "pull_request_review" -> {
    if(!action.equals("submitted"))return;JsonNode review=n.path("review"),pr=n.path("pull_request");Long reviewId=id(review);if(reviewId==null)return;
    activity.event(repo,"review:"+reviewId,"REVIEW",id(review.path("user")),pr.path("number").asInt(),title(pr),url(pr),time(review,"submitted_at",received));activity.requestSync(repo);
   }
   case "issues" -> {
    if(!Set.of("opened","closed").contains(action))return;JsonNode issue=n.path("issue");int number=issue.path("number").asInt();if(number<=0)return;
    Instant at=time(issue,action.equals("closed")?"closed_at":"created_at",received);
    activity.event(repo,"issue-"+action+":"+number+(action.equals("closed")?":"+at:""),action.equals("closed")?"ISSUE_CLOSED":"ISSUE_OPENED",id(issue.path("user")),number,title(issue),url(issue),at);activity.requestSync(repo);
   }
   case "member" -> {Long member=id(n.path("member"));if(member!=null&&Set.of("added","removed").contains(action))collaboration.memberWebhook(repo,member,n.path("member").path("login").asText(""),action.equals("added"));}
   case "repository" -> {
    if(!Set.of("renamed","transferred","privatized","publicized","edited").contains(action))return;
    // Fence an in-flight public snapshot even when private=true is already stored.
    em.lock(l,LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    JsonNode r=n.path("repository");if(r.has("full_name"))l.setFullName(limit(r.path("full_name").asText(),200));if(r.has("html_url"))l.setHtmlUrl(limit(r.path("html_url").asText(),500));if(r.has("description"))l.setDescription(limit(r.path("description").asText(),500));if(r.path("private").asBoolean())l.setRepositoryPrivate(true);/* Only fresh, row-version-fenced sync may downgrade privacy. */if(r.has("default_branch"))l.setDefaultBranch(limit(r.path("default_branch").asText(),1024));activity.requestSync(repo);
   }
   default -> { }
  }
 }
 static Instant time(JsonNode n,String key,Instant fallback){try{return Instant.parse(n.path(key).asText());}catch(Exception ignored){return fallback;}}
 static Long id(JsonNode n){return n.path("id").asLong()>0?n.path("id").longValue():null;}
 private static String title(JsonNode n){return limit(n.path("title").asText(""),300);}
 private static String url(JsonNode n){return n.path("html_url").asText("");}
 private static String limit(String s,int max){return s.substring(0,Math.min(s.length(),max));}
}
