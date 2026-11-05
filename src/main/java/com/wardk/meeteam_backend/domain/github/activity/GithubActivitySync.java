package com.wardk.meeteam_backend.domain.github.activity;
import com.fasterxml.jackson.databind.*;
import com.wardk.meeteam_backend.domain.github.integration.*;
import com.wardk.meeteam_backend.domain.github.repository.GithubAppClient;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import static com.wardk.meeteam_backend.domain.github.activity.GithubActivityWriter.*;
import static com.wardk.meeteam_backend.domain.github.activity.GithubActivityRules.*;
/** Network phase is deliberately outside the short, version-fenced apply transaction. No source/diff endpoints. */
@Service
public class GithubActivitySync implements GithubWorkHandler {
 private final EntityManager em;private final GithubAppClient client;private final GithubActivityWriter writer;private final ObjectMapper json;private final Clock clock;private final TransactionTemplate tx;
 public GithubActivitySync(EntityManager em,GithubAppClient client,GithubActivityWriter writer,ObjectMapper json,@Qualifier("githubActivityClock") Clock clock,PlatformTransactionManager manager){this.em=em;this.client=client;this.writer=writer;this.json=json;this.clock=clock;tx=new TransactionTemplate(manager);}
 public String kind(){return "SYNC";}
 private record Binding(long project,long installation,long repo,long version,long rowVersion,String fullName){}
 private record Event(String key,String type,Long user,Integer number,String title,String url,Instant at){}
 public void handle(Job job){Binding b=tx.execute(s->{var l=em.find(GithubProjectLink.class,job.projectId());return l==null||!l.hasRepository()||l.getBindingVersion()!=job.bindingVersion()?null:new Binding(l.getProjectId(),l.getInstallationId(),l.getGithubRepositoryId(),l.getBindingVersion(),l.getRowVersion(),l.getFullName());});if(b==null)return;
  Instant now=clock.instant(),since=start(monday(now).minusWeeks(7));JsonNode repo=get(b,"/repositories/"+b.repo);validateRepository(b.repo,repo);String base="/repos/"+repo.path("full_name").asText();String branch=repo.path("default_branch").asText();if(branch.isBlank()&&repo.path("size").asLong(-1)!=0)throw new IllegalStateException("Repository default branch unavailable");
  var headReply=client.request(b.installation,"GET",base+"/commits"+(branch.isBlank()?"":"?sha="+enc(branch)),null);
  boolean empty=repo.path("size").asLong(-1)==0&&(headReply.status()==404||headReply.status()==409||(headReply.status()==200&&headReply.body().isArray()&&headReply.body().isEmpty()));
  if(!empty&&(headReply.status()!=200||!headReply.body().isArray()||headReply.body().isEmpty()))throw new IllegalStateException("Default branch metadata unavailable; retry sync");
  Instant pushed=empty?null:time(headReply.body().get(0).path("commit").path("committer"),"date");
  JsonNode languages=get(b,base+"/languages");var bytes=new HashMap<String,Long>();languages.fields().forEachRemaining(e->bytes.put(e.getKey(),e.getValue().asLong()));
  var contributors=(empty?List.<JsonNode>of():client.paginate(b.installation,base+"/contributors",null)).stream().sorted(Comparator.comparingInt((JsonNode n)->n.path("contributions").asInt()).reversed()).limit(10).map(n->{var clean=json.createObjectNode();clean.put("id",n.path("id").asLong());clean.put("login",n.path("login").asText(""));clean.put("avatar_url",n.path("avatar_url").asText(""));clean.put("contributions",n.path("contributions").asInt());return (JsonNode)clean;}).toList();
  var commits=empty?List.<JsonNode>of():client.paginate(b.installation,base+"/commits?sha="+enc(branch)+"&since="+since.atZone(SEOUL).toLocalDate().minusDays(1).toString()+"",null);
  var events=new ArrayList<Event>();var commitTimes=new ArrayList<Instant>();for(var c:commits){Instant at=time(c.path("commit").path("committer"),"date");if(at==null)continue;commitTimes.add(at);events.add(new Event("commit:"+c.path("sha").asText(),"COMMIT",id(c.path("author")),null,"",c.path("html_url").asText(""),at));}
  var prs=client.paginate(b.installation,base+"/pulls?state=all&sort=updated&direction=desc",null);var decisions=reviewDecisions(b,repo);int open=0;var relevant=new ArrayList<JsonNode>();
  for(var pr:prs){boolean isOpen="open".equals(pr.path("state").asText());Instant updated=time(pr,"updated_at");if(!isOpen&&updated!=null&&updated.isBefore(since))continue;relevant.add(pr);if(isOpen){if(!decisions.containsKey(pr.path("number").asInt()))throw new IllegalStateException("Incomplete open PR review metadata");open++;}int n=pr.path("number").asInt();Long author=id(pr.path("user"));Instant created=time(pr,"created_at"),merged=time(pr,"merged_at");if(created!=null&&!created.isBefore(since))events.add(new Event("pr-opened:"+n,"PR_OPENED",author,n,pr.path("title").asText(),pr.path("html_url").asText(),created));if(merged!=null&&!merged.isBefore(since))events.add(new Event("pr-merged:"+n,"PR_MERGED",author,n,pr.path("title").asText(),pr.path("html_url").asText(),merged));
   for(var review:client.paginate(b.installation,base+"/pulls/"+n+"/reviews",null)){Instant at=time(review,"submitted_at");if(at!=null&&!at.isBefore(since))events.add(new Event("review:"+review.path("id").asText(),"REVIEW",id(review.path("user")),n,"",review.path("html_url").asText(),at));}}
  int issues=0;for(var issue:client.paginate(b.installation,base+"/issues?state=all&since="+since.atZone(SEOUL).toLocalDate().minusDays(1).toString()+"",null)){if(issue.has("pull_request"))continue;int n=issue.path("number").asInt();Long author=id(issue.path("user"));String title=issue.path("title").asText(),url=issue.path("html_url").asText();Instant at=time(issue,"created_at"),closed=time(issue,"closed_at");if(at!=null&&!at.isBefore(since))events.add(new Event("issue-opened:"+n,"ISSUE_OPENED",author,n,title,url,at));if(closed!=null&&!closed.isBefore(since))events.add(new Event("issue-closed:"+n+":"+closed,"ISSUE_CLOSED",author,n,title,url,closed));}
  // Open issues must be counted independently of the recent-update backfill window.
  for(var issue:client.paginate(b.installation,base+"/issues?state=open",null))if(!issue.has("pull_request"))issues++;
  final int openCount=open,issueCount=issues;String lang=encode(languages(bytes)),contrib=encode(contributors),weeks=encode(weeks(commitTimes,now));
  tx.executeWithoutResult(s->{var l=em.find(GithubProjectLink.class,b.project,LockModeType.PESSIMISTIC_WRITE);if(l==null)return;em.refresh(l,LockModeType.PESSIMISTIC_WRITE);if(!l.hasRepository()||l.getBindingVersion()!=b.version||!Objects.equals(l.getGithubRepositoryId(),b.repo))return;if(l.getRowVersion()!=b.rowVersion)throw new IllegalStateException("GitHub binding metadata changed; retry sync");
   l.setFullName(repo.path("full_name").asText());l.setHtmlUrl(repo.path("html_url").asText());l.setDescription(repo.path("description").asText(""));l.setRepositoryPrivate(repo.path("private").asBoolean());l.setDefaultBranch(branch);l.setStars(repo.path("stargazers_count").asInt());l.setForks(repo.path("forks_count").asInt());
   var snap=em.find(GithubRepositorySnapshot.class,b.repo);if(snap==null){snap=new GithubRepositorySnapshot(b.repo);em.persist(snap);}if(pushed!=null&&(snap.getLastPushedAt()==null||pushed.isAfter(snap.getLastPushedAt())))snap.setLastPushedAt(pushed);snap.setSyncedAt(now);snap.setLanguages(lang);snap.setContributors(contrib);snap.setWeeklyCounts(weeks);snap.setOpenPullRequestCount(openCount);snap.setOpenIssueCount(issueCount);
   // Never bulk-close rows: stale remote data must not undo a newer webhook.
   Set<Integer> fetched=new HashSet<>();for(var pr:relevant)fetched.add(pr.path("number").asInt());
   for(var p:em.createQuery("select p from GithubPullRequest p where p.repositoryId=:repo and p.open=true",GithubPullRequest.class).setParameter("repo",b.repo).getResultList())if(mayCloseAbsent(p.getNumber(),p.getUpdatedAt(),fetched,now))p.setOpen(false);
   for(var pr:relevant)writer.pullRequest(b.repo,pr,decisions.get(pr.path("number").asInt()));for(var e:events)writer.event(b.repo,e.key,e.type,e.user,e.number,e.title,e.url,e.at);
  });
 }
 private static void validateRepository(long expectedId,JsonNode repo){String name=repo.path("full_name").asText(""),url=repo.path("html_url").asText(""),branch=repo.path("default_branch").asText(""),description=repo.path("description").asText("");if(repo.path("id").asLong()!=expectedId||!name.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")||name.length()>200||!url.equals("https://github.com/"+name)||url.length()>500||description.length()>500||branch.getBytes(StandardCharsets.UTF_8).length>1024)throw new IllegalStateException("Invalid GitHub repository metadata");}
 private Map<Integer,String> reviewDecisions(Binding b,JsonNode repo){String owner=repo.path("owner").path("login").asText(),name=repo.path("name").asText();var result=new HashMap<Integer,String>();String cursor=null;for(int page=0;page<100;page++){var vars=new HashMap<String,Object>();vars.put("owner",owner);vars.put("name",name);vars.put("cursor",cursor);JsonNode body=client.graphQL(b.installation,"query($owner:String!,$name:String!,$cursor:String){repository(owner:$owner,name:$name){pullRequests(states:OPEN,first:100,after:$cursor){nodes{number reviewDecision}pageInfo{hasNextPage endCursor}}}}",vars);JsonNode connection=body.path("data").path("repository").path("pullRequests");if(connection.isMissingNode())connection=body.path("repository").path("pullRequests");if(!connection.path("nodes").isArray())throw new IllegalStateException("Incomplete GitHub review metadata");for(var node:connection.path("nodes"))result.put(node.path("number").asInt(),node.path("reviewDecision").asText("REVIEW_REQUIRED"));if(!connection.path("pageInfo").path("hasNextPage").asBoolean())return result;String next=connection.path("pageInfo").path("endCursor").asText(null);if(next==null||next.equals(cursor))throw new IllegalStateException("Invalid GitHub pagination");cursor=next;}throw new IllegalStateException("GitHub review pagination limit exceeded");}
 private JsonNode get(Binding b,String path){var reply=client.request(b.installation,"GET",path,null);if(reply.status()==202)throw new IllegalStateException("GitHub metadata pending; retry sync");if(reply.status()!=200)throw new IllegalStateException("GitHub metadata unavailable");return reply.body();}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Cannot encode GitHub metadata");}}
 private static String enc(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8).replace("+","%20");}
}
