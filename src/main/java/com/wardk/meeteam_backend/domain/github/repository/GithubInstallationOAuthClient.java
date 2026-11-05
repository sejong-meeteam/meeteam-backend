package com.wardk.meeteam_backend.domain.github.repository;
import com.fasterxml.jackson.databind.*;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthProperties;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class GithubInstallationOAuthClient {
 private final GithubAccountOAuthProperties properties;private final ObjectMapper mapper;private final HttpClient http;
 @Autowired public GithubInstallationOAuthClient(GithubAccountOAuthProperties p,ObjectMapper m){this(p,m,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());}
 public GithubInstallationOAuthClient(GithubAccountOAuthProperties p,ObjectMapper m,HttpClient h){properties=p;mapper=m;http=h;}
 public String callback(){properties.requireEnabled();return properties.getFrontOrigin().replaceAll("/$","")+"/api/v1/github/app/setup/oauth/callback";}
 public String authorize(GithubInstallationState.Issued issued){return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize").queryParam("client_id",properties.getClientId()).queryParam("redirect_uri",callback()).queryParam("state",issued.state()).queryParam("code_challenge",issued.challenge()).queryParam("code_challenge_method","S256").build().encode().toUriString();}
 public Set<Long> prove(String code,String verifier,long installation,long expectedUser){
  properties.requireEnabled();if(code==null||code.length()>2048||code.isBlank()||code.chars().anyMatch(Character::isISOControl)||installation<=0)throw GithubAppClient.invalid();
  String form="client_id="+enc(properties.getClientId())+"&client_secret="+enc(properties.getClientSecret())+"&redirect_uri="+enc(callback())+"&code="+enc(code)+"&code_verifier="+enc(verifier);
  JsonNode token=send("https://github.com/login/oauth/access_token",null,form);String access=token.path("access_token").asText();if(!access.matches("[A-Za-z0-9_]+")||!"bearer".equalsIgnoreCase(token.path("token_type").asText())||token.has("error"))throw GithubAppClient.upstream();
  if(send("https://api.github.com/user",access,null).path("id").asLong()!=expectedUser)throw GithubAppClient.invalid();
  Set<Long> allowed=new HashSet<>();for(int p=1;p<=1000;p++){
   JsonNode rows=send("https://api.github.com/user/installations/"+installation+"/repositories?per_page=100&page="+p,access,null).path("repositories");if(!rows.isArray())throw GithubAppClient.upstream();
   for(JsonNode r:rows)if(r.path("permissions").path("admin").isBoolean()&&r.path("permissions").path("admin").booleanValue()&&r.path("id").isIntegralNumber()&&r.path("id").asLong()>0)allowed.add(r.path("id").asLong());
   if(rows.size()<100)return allowed;
  }throw GithubAppClient.upstream();
 }
 private JsonNode send(String uri,String token,String form){try{
  var b=HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(10)).header("Accept","application/json").header("User-Agent","MeeTeam");
  if(token!=null)b.header("Authorization","Bearer "+token).header("X-GitHub-Api-Version","2022-11-28");
  if(form!=null)b.header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form));else b.GET();
  var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()!=200||r.body()==null||r.body().length()>10_000_000)throw GithubAppClient.upstream();return mapper.readTree(r.body());
 }catch(InterruptedException e){Thread.currentThread().interrupt();throw GithubAppClient.upstream();}catch(Exception e){throw GithubAppClient.upstream();}}
 private static String enc(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
}
