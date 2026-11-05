package com.wardk.meeteam_backend.web.github.repository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.wardk.meeteam_backend.domain.github.integration.GithubProjectLink;
import com.wardk.meeteam_backend.domain.github.repository.GithubRepositoryService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.response.SuccessCode;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import lombok.RequiredArgsConstructor;
import java.net.URI;
import java.util.*;
@RestController @RequiredArgsConstructor
public class GithubRepositoryController {
 private final GithubRepositoryService service;private static final String BASE="/api/v1/projects/{projectId}/github";
 public record Envelope<T>(String code,String message,@JsonInclude(JsonInclude.Include.ALWAYS) T result){}
 public record Repository(long id,String fullName,String htmlUrl,String description,boolean isPrivate,String defaultBranch,int stars,int forks){
  static Repository from(GithubProjectLink l){return new Repository(l.getGithubRepositoryId(),l.getFullName(),l.getHtmlUrl(),l.getDescription()==null?"":l.getDescription(),l.isRepositoryPrivate(),l.getDefaultBranch(),l.getStars(),l.getForks());}
  static Repository from(JsonNode n){return new Repository(n.path("id").asLong(),n.path("full_name").asText(),n.path("html_url").asText(),n.path("description").asText(""),n.path("private").asBoolean(),n.path("default_branch").asText(),n.path("stargazers_count").asInt(),n.path("forks_count").asInt());}
 }
 public record Settings(Boolean autoInviteOnApproval,Boolean autoRevokeOnRemoval){static Settings from(GithubProjectLink l){return new Settings(l.isAutoInviteOnApproval(),l.isAutoRevokeOnRemoval());}}
 public record Connection(boolean isAppInstalled,@JsonInclude(JsonInclude.Include.ALWAYS) Repository repository,Settings settings){static Connection from(GithubProjectLink l){return new Connection(l.isInstallationActive()&&l.getInstallationId()!=null,l.hasRepository()?Repository.from(l):null,Settings.from(l));}}
 public record LinkRequest(Long repositoryId){}
 @GetMapping(BASE) public ResponseEntity<Envelope<Connection>> get(@PathVariable long projectId,@AuthenticationPrincipal CustomSecurityUserDetails u){return ok(Connection.from(service.get(projectId,member(u))));}
 @GetMapping(BASE+"/install-url") public ResponseEntity<Envelope<Map<String,String>>> install(@PathVariable long projectId,@AuthenticationPrincipal CustomSecurityUserDetails u){return ok(Map.of("installUrl",service.installUrl(projectId,member(u))));}
 @GetMapping(BASE+"/repositories") public ResponseEntity<Envelope<List<Repository>>> repositories(@PathVariable long projectId,@AuthenticationPrincipal CustomSecurityUserDetails u){return ok(service.repositories(projectId,member(u)).stream().map(Repository::from).toList());}
 @PutMapping(BASE+"/repository") public ResponseEntity<Envelope<Connection>> link(@PathVariable long projectId,@AuthenticationPrincipal CustomSecurityUserDetails u,@RequestBody LinkRequest body){if(body==null||body.repositoryId()==null)throw new CustomException(ErrorCode.GITHUB_INVALID_REQUEST);return ok(Connection.from(service.link(projectId,member(u),body.repositoryId())));}
 @DeleteMapping(BASE+"/repository") public ResponseEntity<Envelope<Connection>> unlink(@PathVariable long projectId,@AuthenticationPrincipal CustomSecurityUserDetails u){return ok(Connection.from(service.unlink(projectId,member(u))));}
 @PatchMapping(BASE+"/settings") public ResponseEntity<Envelope<Settings>> settings(@PathVariable long projectId,@AuthenticationPrincipal CustomSecurityUserDetails u,@RequestBody Settings s){if(s==null)throw new CustomException(ErrorCode.GITHUB_INVALID_REQUEST);return ok(Settings.from(service.settings(projectId,member(u),s.autoInviteOnApproval(),s.autoRevokeOnRemoval())));}
 @GetMapping("/api/v1/github/app/setup") public ResponseEntity<Void> setup(@RequestParam(name="installation_id",required=false) Long installation,@RequestParam(required=false) String state,@AuthenticationPrincipal CustomSecurityUserDetails u){return redirect(service.setup(installation,state,u==null?null:u.getMemberId()));}
 @GetMapping("/api/v1/github/app/setup/oauth/callback") public ResponseEntity<Void> finish(@RequestParam(required=false) String code,@RequestParam(required=false) String state,@RequestParam(required=false) String error,@AuthenticationPrincipal CustomSecurityUserDetails u){return redirect(service.finish(code,state,error,u==null?null:u.getMemberId()));}
 private static long member(CustomSecurityUserDetails u){if(u==null||u.getMemberId()==null)throw new CustomException(ErrorCode.UNAUTHORIZED);return u.getMemberId();}
 private static <T> ResponseEntity<Envelope<T>> ok(T result){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(new Envelope<>(SuccessCode._OK.getCode(),SuccessCode._OK.getMessage(),result));}
 private static ResponseEntity<Void> redirect(URI uri){return ResponseEntity.status(302).location(uri).cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").build();}
}
