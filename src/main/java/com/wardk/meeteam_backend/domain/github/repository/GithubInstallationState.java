package com.wardk.meeteam_backend.domain.github.repository;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthProperties;
import com.wardk.meeteam_backend.domain.github.repository.GithubRepositoryStore.Context;
import io.jsonwebtoken.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
@Service @RequiredArgsConstructor
public class GithubInstallationState {
 private final GithubAccountOAuthProperties properties;private final StringRedisTemplate redis;
 public record Issued(String state,String challenge){@Override public String toString(){return "Issued[REDACTED]";}}
 public record Validated(Context context,long installationId,String verifier){@Override public String toString(){return "Validated[REDACTED]";}}
 private SecretKeySpec key(){properties.requireEnabled();return new SecretKeySpec(Base64.getDecoder().decode(properties.getStateSecret()),"HmacSHA256");}
 public Issued issue(Context c,String purpose,long installation){
  try{
   String nonce=random(),verifier=random();String state=Jwts.builder().claim("purpose",purpose).claim("p",c.projectId()).claim("m",c.memberId()).claim("a",c.authVersion()).claim("l",c.linkVersion()).claim("g",c.githubUserId()).claim("b",c.bindingVersion()).claim("i",installation).claim("n",nonce).expiration(Date.from(Instant.now().plusSeconds(600))).signWith(key(),Jwts.SIG.HS256).compact();
   if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("github:install:"+nonce,verifier,Duration.ofMinutes(10))))throw GithubAppClient.invalid();
   return new Issued(state,Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))));
  }catch(Exception e){throw GithubAppClient.invalid();}
 }
 public Validated consume(String state,String purpose){try{
  if(state==null||state.length()>4096)throw GithubAppClient.invalid();
  Claims c=Jwts.parser().verifyWith(key()).sig().clear().add(Jwts.SIG.HS256).and().build().parseSignedClaims(state).getPayload();
  if(!purpose.equals(c.get("purpose"))||c.getExpiration()==null||c.getExpiration().toInstant().isAfter(Instant.now().plusSeconds(600)))throw GithubAppClient.invalid();
  String nonce=c.get("n",String.class);if(nonce==null||!nonce.matches("[A-Za-z0-9_-]{43}"))throw GithubAppClient.invalid();
  Context context=new Context(num(c,"p"),num(c,"m"),num(c,"a"),num(c,"l"),num(c,"g"),num(c,"b"));long id=num(c,"i");
  if(context.projectId()<=0||context.memberId()<=0||context.githubUserId()<=0||context.authVersion()<0||context.linkVersion()<0||context.bindingVersion()<0||id<0)throw GithubAppClient.invalid();
  String verifier=redis.opsForValue().getAndDelete("github:install:"+nonce);if(verifier==null||!verifier.matches("[A-Za-z0-9_-]{43}"))throw GithubAppClient.invalid();return new Validated(context,id,verifier);
 }catch(Exception e){throw GithubAppClient.invalid();}}
 private static long num(Claims c,String n){Object v=c.get(n);if(!(v instanceof Integer||v instanceof Long))throw GithubAppClient.invalid();return ((Number)v).longValue();}
 private static String random(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
}
