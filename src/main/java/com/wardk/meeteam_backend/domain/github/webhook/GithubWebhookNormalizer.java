package com.wardk.meeteam_backend.domain.github.webhook;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Set;
/** Explicit metadata allowlist. Never copy arbitrary subtrees into durable storage. */
@Component
public class GithubWebhookNormalizer {
 private final ObjectMapper mapper;
 public static final Set<String> EVENTS=Set.of("installation","installation_repositories","push","pull_request","pull_request_review","issues","member","repository");
 public GithubWebhookNormalizer(ObjectMapper mapper){this.mapper=mapper;}
 public ObjectNode normalize(String event,JsonNode raw,Instant received){
  if(raw==null||!raw.isObject())throw new GithubWebhookException(400);
  ObjectNode out=mapper.createObjectNode();out.put("event",event);out.put("received_at",received.toString());text(raw,out,"action",64);text(raw,out,"ref",1035);
  out.set("installation",object(raw.path("installation"),"id"));
  ObjectNode repo=object(raw.path("repository"),"id","private","pushed_at");
  for(String key:new String[]{"full_name","html_url","description","default_branch"})text(raw.path("repository"),repo,key,key.equals("default_branch")?1024:500);
  out.set("repository",repo);
  for(String key:new String[]{"pull_request","issue"}){
   JsonNode n=raw.path(key);if(!n.isObject())continue;ObjectNode obj=object(n,"id","number","draft","merged");
   for(String k:new String[]{"title","html_url","state","created_at","updated_at","closed_at","merged_at"})text(n,obj,k,k.equals("title")?300:500);
   for(String timeKey:new String[]{"created_at","updated_at","closed_at","merged_at"}){
    if(timeKey.equals("created_at")||timeKey.equals("updated_at")||n.path(timeKey).isTextual()){
     try{obj.put(timeKey,Instant.parse(n.path(timeKey).asText()).toString());}catch(Exception ignored){obj.put(timeKey,received.toString());}
    }
   }
   obj.set("user",user(n.path("user")));out.set(key,obj);
  }
  if(raw.path("review").isObject()) {JsonNode n=raw.path("review");ObjectNode r=object(n,"id");text(n,r,"state",40);text(n,r,"submitted_at",64);r.set("user",user(n.path("user")));out.set("review",r);}
  for(String k:new String[]{"member","requested_reviewer"})out.set(k,user(raw.path(k)));
  ArrayNode commits=out.putArray("commits");if(raw.path("commits").isArray())for(JsonNode c:raw.path("commits")){
   ObjectNode n=mapper.createObjectNode();sha(c,n,"id");text(c,n,"timestamp",64);
   ObjectNode author=mapper.createObjectNode();text(c.path("author"),author,"username",100);n.set("author",author);commits.add(n);
  }
  ObjectNode head=mapper.createObjectNode();sha(raw.path("head_commit"),head,"id");text(raw.path("head_commit"),head,"timestamp",64);out.set("head_commit",head);
  ArrayNode removed=out.putArray("repositories_removed");if(raw.path("repositories_removed").isArray())for(JsonNode r:raw.path("repositories_removed"))removed.add(object(r,"id"));
  return out;
 }
 private ObjectNode object(JsonNode from,String...keys){ObjectNode n=mapper.createObjectNode();for(String k:keys){JsonNode v=from.path(k);if(v.isIntegralNumber()&&v.canConvertToLong()&&v.longValue()>0)n.put(k,v.longValue());else if(v.isBoolean())n.put(k,v.booleanValue());}return n;}
 private ObjectNode user(JsonNode n){ObjectNode u=object(n,"id");String login=n.path("login").asText("");if(login.matches("[A-Za-z0-9-]{1,100}"))u.put("login",login);return u;}
 private void text(JsonNode from,ObjectNode to,String k,int max){JsonNode v=from.path(k);if(v.isTextual()){if((k.equals("ref")||k.equals("default_branch"))&&v.textValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>max)throw new GithubWebhookException(400);to.put(k,v.textValue().substring(0,Math.min(max,v.textValue().length())));}}
 private void sha(JsonNode from,ObjectNode to,String k){String v=from.path(k).asText("");if(v.matches("[a-fA-F0-9]{40,64}"))to.put(k,v.toLowerCase());}
}
