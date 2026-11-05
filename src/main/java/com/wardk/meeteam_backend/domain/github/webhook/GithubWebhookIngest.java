package com.wardk.meeteam_backend.domain.github.webhook;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.github.integration.GithubWorkQueue;
import com.wardk.meeteam_backend.domain.github.repository.GithubAppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
@Service
public class GithubWebhookIngest {
 private final GithubAppProperties properties;private final ObjectMapper mapper;private final GithubWebhookNormalizer normalizer;private final GithubWorkQueue queue;
 public GithubWebhookIngest(GithubAppProperties p,ObjectMapper m,GithubWebhookNormalizer n,GithubWorkQueue q){properties=p;mapper=m;normalizer=n;queue=q;}
 public void verify(byte[] body,String signature){
  if(!properties.isEnabled()||properties.getWebhookSecret()==null||properties.getWebhookSecret().isBlank())throw new GithubWebhookException(503);
  if(signature==null||!signature.matches("sha256=[a-fA-F0-9]{64}"))throw new GithubWebhookException(401);
  try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));if(!MessageDigest.isEqual(mac.doFinal(body),HexFormat.of().parseHex(signature.substring(7))))throw new GithubWebhookException(401);}
  catch(GithubWebhookException e){throw e;}catch(Exception ignored){throw new GithubWebhookException(503);}
 }
 @Transactional public void accept(byte[] body,String signature,String delivery,String event){
  verify(body,signature);
  if(delivery==null||!delivery.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")||event==null||!event.matches("[a-z_]{1,64}"))throw new GithubWebhookException(400);
  try{
   com.fasterxml.jackson.databind.JsonNode raw=mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);if(raw==null||!raw.isObject())throw new GithubWebhookException(400);
   if(!GithubWebhookNormalizer.EVENTS.contains(event))return;
   String key="delivery:"+delivery.toLowerCase(java.util.Locale.ROOT);if(queue.exists(key))return;
   queue.enqueue("WEBHOOK",null,null,0,mapper.writeValueAsString(normalizer.normalize(event,raw,Instant.now())),key);
  }catch(GithubWebhookException e){throw e;}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new GithubWebhookException(400);}catch(java.io.IOException e){throw new GithubWebhookException(400);}
 }
 public boolean accepted(String delivery){return delivery!=null&&queue.exists("delivery:"+delivery.toLowerCase(java.util.Locale.ROOT));}
}
