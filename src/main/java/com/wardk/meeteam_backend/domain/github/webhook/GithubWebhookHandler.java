package com.wardk.meeteam_backend.domain.github.webhook;
import com.fasterxml.jackson.databind.*;
import com.wardk.meeteam_backend.domain.github.integration.GithubWorkHandler;
import com.wardk.meeteam_backend.domain.github.repository.GithubAppClient;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class GithubWebhookHandler implements GithubWorkHandler {
 private final ObjectMapper mapper;private final GithubWebhookProcessor processor;private final GithubWebhookAuthorContext context;private final GithubAppClient client;
 public GithubWebhookHandler(ObjectMapper m,GithubWebhookProcessor p,GithubWebhookAuthorContext c,GithubAppClient a){mapper=m;processor=p;context=c;client=a;}
 public String kind(){return "WEBHOOK";}
 public void handle(Job job){
  JsonNode n;try{n=mapper.readTree(job.payload());}catch(Exception ignored){throw new IllegalStateException("Invalid webhook metadata");}
  Map<String,Long> authors=new HashMap<>();
  if(n.path("event").asText().equals("push")){
   long installation=n.path("installation").path("id").asLong();var repo=context.repository(installation,n.path("repository").path("id").asLong());
   if(repo!=null&&repo.fullName()!=null&&repo.fullName().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")&&n.path("ref").asText().equals("refs/heads/"+repo.defaultBranch())){
    String[] names=repo.fullName().split("/");
    for(JsonNode c:n.path("commits")){String sha=c.path("id").asText();if(!sha.matches("[a-f0-9]{40,64}"))continue;
     try{JsonNode data=client.graphQL(installation,"query($owner:String!,$name:String!,$sha:String!){repository(owner:$owner,name:$name){object(expression:$sha){... on Commit{author{user{databaseId login}}}}}}",Map.of("owner",names[0],"name",names[1],"sha",sha));long id=data.path("repository").path("object").path("author").path("user").path("databaseId").asLong();if(id>0)authors.put(sha,id);}catch(RuntimeException ignored){/* Anonymous/unavailable author: preserve commit count, reconcile during sync. */}
    }
   }
  }
  processor.apply(job.id(),n,authors);
 }
}
