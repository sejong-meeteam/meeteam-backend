package com.wardk.meeteam_backend.domain.github.repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.wardk.meeteam_backend.domain.github.integration.GithubProjectLink;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthProperties;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import java.net.URI;
import java.util.*;
@Service @RequiredArgsConstructor
public class GithubRepositoryService {
 private final GithubRepositoryStore store;private final GithubAppClient client;private final GithubAppProperties app;
 private final GithubAccountOAuthProperties account;private final GithubInstallationState states;private final GithubInstallationOAuthClient oauth;
 public GithubProjectLink get(long p,long m){return store.get(p,m);}
 public String installUrl(long p,long m){app.requireEnabled();var c=store.context(p,m);var issued=states.issue(c,"install",0);return "https://github.com/apps/"+app.getSlug()+"/installations/new?state="+issued.state();}
 public List<JsonNode> repositories(long p,long m){var l=store.get(p,m);GithubRepositoryStore.requireInstalled(l);Set<Long> allowed=store.allowed(l),taken=store.taken(p);return client.paginate(l.getInstallationId(),"/installation/repositories","repositories").stream().filter(r->allowed.contains(r.path("id").asLong())&&!taken.contains(r.path("id").asLong())).toList();}
 public GithubProjectLink link(long p,long m,long id){
  if(id<=0)throw GithubAppClient.invalid();var c=store.context(p,m);var l=store.get(p,m);GithubRepositoryStore.requireInstalled(l);
  if(!store.allowed(l).contains(id))throw GithubAppClient.invalid();
  JsonNode repo=client.request(l.getInstallationId(),"GET","/repositories/"+id,null).body();
  if(repo.path("id").asLong()!=id||!repo.path("full_name").asText().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")||!repo.path("html_url").asText().startsWith("https://github.com/"))throw GithubAppClient.upstream();
  String branch=repo.path("default_branch").asText("");
  if(branch.isBlank()||branch.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1024||repo.path("full_name").asText().length()>200||repo.path("html_url").asText().length()>500||repo.path("description").asText("").length()>500)throw GithubAppClient.upstream();
  try{return store.link(c,l.getInstallationId(),repo);}catch(DataIntegrityViolationException e){throw new CustomException(ErrorCode.GITHUB_REPOSITORY_TAKEN);}
 }
 public GithubProjectLink unlink(long p,long m){return store.unlink(p,m);}
 public GithubProjectLink settings(long p,long m,Boolean i,Boolean r){return store.settings(p,m,i,r);}
 public URI setup(Long installation,String state,Long member){long project=0;try{
  var s=states.consume(state,"install");project=s.context().projectId();if(member==null||member!=s.context().memberId()||installation==null||installation<=0)throw GithubAppClient.invalid();store.validate(s.context());
  return URI.create(oauth.authorize(states.issue(s.context(),"install-oauth",installation)));
 }catch(RuntimeException e){return result(project,false);}}
 public URI finish(String code,String state,String error,Long member){long project=0;try{
  var s=states.consume(state,"install-oauth");project=s.context().projectId();if(member==null||member!=s.context().memberId()||error!=null)throw GithubAppClient.invalid();store.validate(s.context());
  Set<Long> ids=oauth.prove(code,s.verifier(),s.installationId(),s.context().githubUserId());
  JsonNode installation=client.getInstallation(s.installationId());
  if(!installation.path("id").isIntegralNumber()||installation.path("id").asLong()!=s.installationId()||!app.getAppId().equals(installation.path("app_id").asText())||!installation.has("suspended_at")||!installation.path("suspended_at").isNull())throw GithubAppClient.invalid();
  store.install(s.context(),s.installationId(),ids);return result(project,true);
 }catch(RuntimeException e){return result(project,false);}}
 private URI result(long project,boolean ok){account.requireEnabled();return URI.create(account.getFrontOrigin().replaceAll("/$","")+(project>0?"/projects/"+project+"/manage/github?installed="+(ok?"1":"0"):"/settings?github=failed"));}
}
